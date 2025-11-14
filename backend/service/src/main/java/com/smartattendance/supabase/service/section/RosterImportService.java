package com.smartattendance.supabase.service.section;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.smartattendance.supabase.dto.RosterImportIssueDto;
import com.smartattendance.supabase.dto.RosterImportResponse;
import com.smartattendance.supabase.dto.StudentDto;
import com.smartattendance.supabase.entity.ProfileEntity;
import com.smartattendance.supabase.repository.ProfileRepository;
import com.smartattendance.supabase.service.profile.StudentDirectoryService;

@Service
public class RosterImportService {

    private static final int MAX_ROWS = 1000;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("\\b[A-Za-z]{1,3}\\d{4,}[A-Za-z0-9]*\\b");
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
    private static final Set<String> HEADER_HINTS = Set.of("name", "email", "student", "id", "matric", "identifier");

    private final ProfileRepository profileRepository;
    private final StudentDirectoryService studentDirectoryService;

    public RosterImportService(ProfileRepository profileRepository,
                               StudentDirectoryService studentDirectoryService) {
        this.profileRepository = profileRepository;
        this.studentDirectoryService = studentDirectoryService;
    }

    public RosterImportResponse importRoster(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Roster file is required");
        }
        ParsedResult parsed = parseRows(file);
        List<ParsedRow> rows = parsed.rows();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file does not contain any roster entries");
        }

        List<RosterImportIssueDto> issues = new ArrayList<>();
        LinkedHashSet<UUID> uniqueStudentIds = new LinkedHashSet<>();
        int duplicateCount = 0;
        int processed = 0;

        for (ParsedRow row : rows) {
            processed++;
            Resolution resolution = resolveRow(row);
            if (resolution.status == ResolutionStatus.MATCH && resolution.studentId != null) {
                if (!uniqueStudentIds.add(resolution.studentId)) {
                    duplicateCount++;
                    issues.add(issue(row.rowNumber(), resolution.displayValue,
                            "Duplicate student detected in the uploaded file"));
                }
            } else if (resolution.status == ResolutionStatus.ERROR) {
                issues.add(issue(row.rowNumber(), resolution.displayValue, resolution.message));
            }
        }

        if (parsed.truncated()) {
            issues.add(issue(MAX_ROWS + 1, null,
                    "Additional rows were skipped because the file exceeds the " + MAX_ROWS + " row limit."));
        }

        List<StudentDto> students = studentDirectoryService.listByIds(uniqueStudentIds);
        RosterImportResponse response = new RosterImportResponse();
        response.setProcessedCount(processed);
        response.setMatchedCount(students.size());
        response.setDuplicateCount(duplicateCount);
        response.setStudents(students);
        response.setIssues(issues);
        return response;
    }

    private Resolution resolveRow(ParsedRow row) {
        List<String> candidates = expandCandidates(row.values());
        if (candidates.isEmpty()) {
            return Resolution.failed("No usable identifiers found in this row", null);
        }
        for (String candidate : candidates) {
            Resolution attempt = resolveCandidate(candidate);
            if (attempt.status == ResolutionStatus.MATCH || attempt.status == ResolutionStatus.ERROR) {
                return attempt;
            }
        }
        return Resolution.failed("No matching student found", String.join(" | ", row.values()));
    }

    private Resolution resolveCandidate(String candidate) {
        String value = candidate != null ? candidate.trim() : "";
        if (value.isEmpty()) {
            return Resolution.miss();
        }

        Optional<ProfileEntity> profile = Optional.empty();
        if (looksLikeUuid(value)) {
            profile = profileRepository.findById(UUID.fromString(value));
        }
        if (profile.isEmpty() && looksLikeEmail(value)) {
            profile = profileRepository.findByEmailIgnoreCase(value);
        }
        if (profile.isEmpty()) {
            profile = profileRepository.findByStudentIdentifier(value);
            if (profile.isEmpty()) {
                profile = profileRepository.findByStaffId(value);
            }
        }
        if (profile.isEmpty()) {
            List<ProfileEntity> matches = profileRepository.findActiveStudentsByFullName(value);
            if (matches.size() == 1) {
                profile = Optional.of(matches.get(0));
            } else if (matches.size() > 1) {
                return Resolution.failed("Multiple students share the name '" + value + "'", value);
            }
        }

        if (profile.isEmpty()) {
            return Resolution.miss();
        }

        ProfileEntity entity = profile.get();
        if (!isActiveStudent(entity)) {
            return Resolution.failed("Matched profile is not an active student", value);
        }
        return Resolution.matched(entity.getId(), value);
    }

    private boolean looksLikeEmail(String value) {
        return EMAIL_PATTERN.matcher(value).find();
    }

    private boolean looksLikeUuid(String value) {
        return UUID_PATTERN.matcher(value).matches();
    }

    private ParsedResult parseRows(MultipartFile file) {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        try {
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                return parseSpreadsheet(file);
            }
            return parseCsv(file);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read roster file", ex);
        }
    }

    private ParsedResult parseCsv(MultipartFile file) throws IOException {
        List<ParsedRow> rows = new ArrayList<>();
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setIgnoreEmptyLines(true)
                        .setTrim(true)
                        .build()
                        .parse(reader)) {
            int recordIndex = 0;
            for (CSVRecord record : parser) {
                recordIndex++;
                List<String> values = new ArrayList<>();
                record.forEach(value -> {
                    String sanitized = sanitizeValue(value);
                    if (!sanitized.isEmpty()) {
                        values.add(sanitized);
                    }
                });
                if (values.isEmpty()) {
                    continue;
                }
                if (recordIndex == 1 && looksLikeHeader(values)) {
                    continue;
                }
                rows.add(new ParsedRow(recordIndex, values));
                if (rows.size() >= MAX_ROWS) {
                    truncated = true;
                    break;
                }
            }
        }
        return new ParsedResult(rows, truncated);
    }

    private ParsedResult parseSpreadsheet(MultipartFile file) throws IOException {
        List<ParsedRow> rows = new ArrayList<>();
        boolean truncated = false;
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return new ParsedResult(List.of(), false);
            }
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                int rowNumber = row.getRowNum() + 1;
                List<String> values = new ArrayList<>();
                for (Cell cell : row) {
                    String formatted = sanitizeValue(formatter.formatCellValue(cell));
                    if (!formatted.isEmpty()) {
                        values.add(formatted);
                    }
                }
                if (values.isEmpty()) {
                    continue;
                }
                if (rowNumber == 1 && looksLikeHeader(values)) {
                    continue;
                }
                rows.add(new ParsedRow(rowNumber, values));
                if (rows.size() >= MAX_ROWS) {
                    truncated = true;
                    break;
                }
            }
        }
        return new ParsedResult(rows, truncated);
    }

    private boolean looksLikeHeader(List<String> values) {
        if (values.isEmpty()) {
            return false;
        }
        long matches = values.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> HEADER_HINTS.stream().anyMatch(value::contains))
                .count();
        return matches >= Math.min(2, values.size());
    }

    private List<String> expandCandidates(List<String> rawValues) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String raw : rawValues) {
            if (raw == null) {
                continue;
            }
            String sanitized = sanitizeValue(raw);
            if (sanitized.isEmpty()) {
                continue;
            }
            addCandidate(candidates, sanitized);
            String stripped = sanitized.replace("(", " ")
                    .replace(")", " ")
                    .replace("<", " ")
                    .replace(">", " ")
                    .replace("[", " ")
                    .replace("]", " ");
            for (String part : stripped.split("[;,|]")) {
                String trimmed = sanitizeValue(part);
                if (!trimmed.isEmpty()) {
                    addCandidate(candidates, trimmed);
                }
            }
        }
        return List.copyOf(candidates);
    }

    private void addCandidate(Set<String> candidates, String value) {
        if (value.isEmpty()) {
            return;
        }
        candidates.add(value);
        Matcher emailMatcher = EMAIL_PATTERN.matcher(value);
        while (emailMatcher.find()) {
            candidates.add(emailMatcher.group());
        }
        Matcher idMatcher = STUDENT_ID_PATTERN.matcher(value);
        while (idMatcher.find()) {
            String match = sanitizeValue(idMatcher.group());
            if (!match.isEmpty()) {
                candidates.add(match);
            }
        }
    }

    private String sanitizeValue(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private boolean isActiveStudent(ProfileEntity profile) {
        if (profile == null) {
            return false;
        }
        boolean active = profile.getActive() == null || Boolean.TRUE.equals(profile.getActive());
        return active && profile.getRole() == ProfileEntity.Role.student;
    }

    private RosterImportIssueDto issue(int rowNumber, String value, String reason) {
        RosterImportIssueDto issue = new RosterImportIssueDto();
        issue.setRowNumber(rowNumber);
        issue.setValue(value);
        issue.setReason(reason);
        return issue;
    }

    private record ParsedRow(int rowNumber, List<String> values) {
    }

    private record ParsedResult(List<ParsedRow> rows, boolean truncated) {
    }

    private enum ResolutionStatus {
        MATCH,
        MISS,
        ERROR
    }

    private static class Resolution {
        private final ResolutionStatus status;
        private final UUID studentId;
        private final String displayValue;
        private final String message;

        private Resolution(ResolutionStatus status, UUID studentId, String displayValue, String message) {
            this.status = status;
            this.studentId = studentId;
            this.displayValue = displayValue;
            this.message = message;
        }

        private static Resolution matched(UUID studentId, String displayValue) {
            return new Resolution(ResolutionStatus.MATCH, studentId, displayValue, null);
        }

        private static Resolution failed(String message, String displayValue) {
            return new Resolution(ResolutionStatus.ERROR, null, displayValue, message);
        }

        private static Resolution miss() {
            return new Resolution(ResolutionStatus.MISS, null, null, null);
        }
    }
}
