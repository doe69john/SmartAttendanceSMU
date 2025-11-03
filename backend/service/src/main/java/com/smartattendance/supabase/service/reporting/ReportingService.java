package com.smartattendance.supabase.service.reporting;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.smartattendance.supabase.dto.ProfessorSectionReportDto;
import com.smartattendance.supabase.dto.ProfessorStudentReportDto;
import com.smartattendance.supabase.dto.SectionAnalyticsDto;
import com.smartattendance.supabase.dto.SectionReportDetailDto;
import com.smartattendance.supabase.dto.SessionSummaryDto;
import com.smartattendance.supabase.dto.StudentAttendanceHistoryDto;
import com.smartattendance.supabase.dto.StudentReportDetailDto;
import com.smartattendance.supabase.dto.StudentSectionReportDto;
import com.smartattendance.supabase.dto.admin.AdminSectionSummaryDto;
import com.smartattendance.supabase.repository.ReportingJdbcRepository;
import com.smartattendance.supabase.repository.ReportingJdbcRepository.SectionAttendanceExportRow;
import com.smartattendance.supabase.service.admin.AdminSectionService;
import com.smartattendance.supabase.service.session.SessionQueryService;

@Service
public class ReportingService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final ReportingJdbcRepository reportingRepository;
    private final TeachingManagementService teachingManagementService;
    private final SessionQueryService sessionQueryService;
    private final AdminSectionService adminSectionService;

    public ReportingService(ReportingJdbcRepository reportingRepository,
                            TeachingManagementService teachingManagementService,
                            SessionQueryService sessionQueryService,
                            AdminSectionService adminSectionService) {
        this.reportingRepository = reportingRepository;
        this.teachingManagementService = teachingManagementService;
        this.sessionQueryService = sessionQueryService;
        this.adminSectionService = adminSectionService;
    }

    public List<ProfessorStudentReportDto> listProfessorStudents(UUID professorId, String query) {
        return reportingRepository.findProfessorStudents(professorId, sanitize(query));
    }

    public List<ProfessorStudentReportDto> listAdminStudents(String query) {
        return reportingRepository.findAdminStudents(sanitize(query));
    }

    public Optional<StudentReportDetailDto> loadStudentReport(UUID professorId, UUID studentId) {
        Optional<ProfessorStudentReportDto> summary = reportingRepository.findProfessorStudent(professorId, studentId);
        if (summary.isEmpty()) {
            return Optional.empty();
        }
        StudentReportDetailDto detail = new StudentReportDetailDto();
        detail.setSummary(summary.get());
        detail.setSections(reportingRepository.findStudentSections(professorId, studentId));
        detail.setAttendanceHistory(reportingRepository.findStudentAttendanceHistory(professorId, studentId));
        return Optional.of(detail);
    }

    public Optional<StudentReportDetailDto> loadStudentReportForAdmin(UUID studentId) {
        Optional<ProfessorStudentReportDto> summary = reportingRepository.findStudentSelfSummary(studentId);
        if (summary.isEmpty()) {
            return Optional.empty();
        }
        StudentReportDetailDto detail = new StudentReportDetailDto();
        detail.setSummary(summary.get());
        detail.setSections(reportingRepository.findStudentSelfSections(studentId, null));
        detail.setAttendanceHistory(reportingRepository.findStudentSelfAttendanceHistory(studentId, null));
        return Optional.of(detail);
    }

    public List<ProfessorSectionReportDto> listProfessorSections(UUID professorId, String query) {
        return teachingManagementService.searchSectionsForProfessor(professorId, sanitize(query), null, null).stream()
                .map(section -> {
                    SectionAnalyticsDto analytics = sessionQueryService.loadSectionAnalytics(section.getId());
                    ProfessorSectionReportDto dto = new ProfessorSectionReportDto();
                    dto.setSectionId(section.getId());
                    dto.setCourseId(section.getCourseId());
                    dto.setCourseCode(section.getCourseCode());
                    dto.setCourseTitle(section.getCourseTitle());
                    dto.setSectionCode(section.getSectionCode());
                    dto.setLocation(section.getLocation());
                    dto.setEnrolledStudents(section.getEnrolledCount());
                    dto.setMaxStudents(section.getMaxStudents());
                    dto.setTotalSessions(analytics.getTotalSessions());
                    dto.setCompletedSessions(analytics.getCompletedSessions());
                    dto.setUpcomingSessions(analytics.getUpcomingSessions());
                    dto.setAverageAttendanceRate(analytics.getAveragePresentRate());
                    dto.setAveragePresentRate(analytics.getAveragePresentRate());
                    dto.setAverageLateRate(analytics.getAverageLateRate());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<ProfessorSectionReportDto> listAdminSections(String query) {
        String normalizedQuery = sanitize(query);
        return adminSectionService.listSections(normalizedQuery).stream()
                .map(section -> {
                    SectionAnalyticsDto analytics = sessionQueryService.loadSectionAnalytics(section.getSectionId());
                    return toSectionSummary(section, analytics);
                })
                .collect(Collectors.toList());
    }

    public Optional<SectionReportDetailDto> loadSectionReport(UUID professorId, UUID sectionId) {
        Optional<SectionAttendanceExportRow> metadata = reportingRepository.findSectionMetadata(professorId, sectionId);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        SectionAnalyticsDto analytics = sessionQueryService.loadSectionAnalytics(sectionId);
        SectionReportDetailDto detail = new SectionReportDetailDto();
        detail.setSummary(toSectionSummary(metadata.get(), analytics));
        List<SessionSummaryDto> sessions = teachingManagementService.findSessionsForSection(sectionId);
        sessions.sort(Comparator.comparing(SessionSummaryDto::getSessionDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SessionSummaryDto::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
        detail.setSessions(sessions);
        return Optional.of(detail);
    }

    public Optional<SectionReportDetailDto> loadSectionReportForAdmin(UUID sectionId) {
        Optional<SectionAttendanceExportRow> metadata = reportingRepository.findSectionMetadataForAdmin(sectionId);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        SectionAnalyticsDto analytics = sessionQueryService.loadSectionAnalytics(sectionId);
        SectionReportDetailDto detail = new SectionReportDetailDto();
        detail.setSummary(toSectionSummary(metadata.get(), analytics));
        List<SessionSummaryDto> sessions = teachingManagementService.findSessionsForSection(sectionId);
        sessions.sort(Comparator.comparing(SessionSummaryDto::getSessionDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SessionSummaryDto::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
        detail.setSessions(sessions);
        return Optional.of(detail);
    }

    public Optional<ReportExport> createSectionExport(UUID professorId,
                                                      UUID sectionId,
                                                      ExportFormat format,
                                                      UUID sessionId) {
        Optional<SectionAttendanceExportRow> metadata = reportingRepository.findSectionMetadata(professorId, sectionId);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        List<SectionAttendanceExportRow> rows = reportingRepository.findSectionAttendance(professorId, sectionId);
        if (sessionId != null) {
            rows = rows.stream()
                    .filter(row -> sessionId.equals(row.getSessionId()))
                    .collect(Collectors.toList());
        }
        String baseName = buildSectionFilename(metadata.get());
        return Optional.of(generateExport(format, baseName, rows));
    }

    public Optional<ReportExport> createSectionExportForAdmin(UUID sectionId,
                                                              ExportFormat format,
                                                              UUID sessionId) {
        Optional<SectionAttendanceExportRow> metadata = reportingRepository.findSectionMetadataForAdmin(sectionId);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        List<SectionAttendanceExportRow> rows = reportingRepository.findSectionAttendanceForAdmin(sectionId);
        if (sessionId != null) {
            rows = rows.stream()
                    .filter(row -> sessionId.equals(row.getSessionId()))
                    .collect(Collectors.toList());
        }
        String baseName = buildSectionFilename(metadata.get());
        return Optional.of(generateExport(format, baseName, rows));
    }

    public Optional<ReportExport> createStudentExport(UUID professorId, UUID studentId, ExportFormat format) {
        Optional<StudentReportDetailDto> detail = loadStudentReport(professorId, studentId);
        if (detail.isEmpty()) {
            return Optional.empty();
        }
        StudentReportDetailDto report = detail.get();
        String baseName = buildStudentFilename(report.getSummary());
        return Optional.of(generateStudentExport(format, baseName, report));
    }

    public Optional<ReportExport> createStudentExportForAdmin(UUID studentId, ExportFormat format) {
        Optional<StudentReportDetailDto> detail = loadStudentReportForAdmin(studentId);
        if (detail.isEmpty()) {
            return Optional.empty();
        }
        ProfessorStudentReportDto summary = detail.get().getSummary();
        String baseName = buildStudentFilename(summary);
        return Optional.of(generateStudentExport(format, baseName, detail.get()));
    }

    public Optional<StudentReportDetailDto> loadStudentSelfReport(UUID studentId, UUID sectionId) {
        Optional<ProfessorStudentReportDto> baseSummary = reportingRepository.findStudentSelfSummary(studentId);
        if (baseSummary.isEmpty()) {
            return Optional.empty();
        }

        List<StudentSectionReportDto> sections = reportingRepository.findStudentSelfSections(studentId, sectionId);
        List<StudentAttendanceHistoryDto> attendance = reportingRepository.findStudentSelfAttendanceHistory(studentId, sectionId);

        if (sectionId != null && sections.isEmpty()) {
            return Optional.empty();
        }

        StudentReportDetailDto detail = new StudentReportDetailDto();
        ProfessorStudentReportDto summary = sectionId == null
                ? baseSummary.get()
                : buildStudentSummary(baseSummary.get(), sections, attendance);

        detail.setSummary(summary);
        detail.setSections(sections);
        detail.setAttendanceHistory(attendance);
        return Optional.of(detail);
    }

    public Optional<ReportExport> createStudentSelfExport(UUID studentId, ExportFormat format, UUID sectionId) {
        Optional<StudentReportDetailDto> detail = loadStudentSelfReport(studentId, sectionId);
        if (detail.isEmpty()) {
            return Optional.empty();
        }
        ProfessorStudentReportDto summary = detail.get().getSummary();
        String baseName = buildStudentFilename(summary);
        return Optional.of(generateStudentExport(format, baseName, detail.get()));
    }

    private ReportExport generateExport(ExportFormat format, String baseName, List<SectionAttendanceExportRow> rows) {
        return switch (format) {
            case CSV -> new ReportExport(buildSectionCsv(rows).getBytes(StandardCharsets.UTF_8),
                    "text/csv",
                    baseName + ".csv");
            case XLSX -> new ReportExport(buildSectionWorkbook(rows),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baseName + ".xlsx");
        };
    }

    private ReportExport generateStudentExport(ExportFormat format, String baseName, StudentReportDetailDto detail) {
        return switch (format) {
            case CSV -> new ReportExport(buildStudentCsv(detail).getBytes(StandardCharsets.UTF_8),
                    "text/csv",
                    baseName + ".csv");
            case XLSX -> new ReportExport(buildStudentWorkbook(detail),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baseName + ".xlsx");
        };
    }

    private ProfessorStudentReportDto buildStudentSummary(ProfessorStudentReportDto base,
                                                          List<StudentSectionReportDto> sections,
                                                          List<StudentAttendanceHistoryDto> attendance) {
        ProfessorStudentReportDto summary = new ProfessorStudentReportDto();
        summary.setStudent(base.getStudent());

        int sectionCount = !sections.isEmpty()
                ? sections.size()
                : (int) attendance.stream()
                        .map(StudentAttendanceHistoryDto::getSectionId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();
        summary.setSectionCount(sectionCount > 0 ? sectionCount : base.getSectionCount());

        int courseCount = !sections.isEmpty()
                ? (int) sections.stream()
                        .map(StudentSectionReportDto::getCourseId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count()
                : (int) attendance.stream()
                        .map(StudentAttendanceHistoryDto::getCourseCode)
                        .filter(Objects::nonNull)
                        .map(code -> code.toLowerCase(Locale.ROOT))
                        .distinct()
                        .count();
        summary.setCourseCount(courseCount > 0 ? courseCount : base.getCourseCount());

        if (!attendance.isEmpty()) {
            int totalSessions = attendance.size();
            int recordedSessions = (int) attendance.stream()
                    .map(StudentAttendanceHistoryDto::getStatus)
                    .filter(ReportingService::hasStatus)
                    .count();
            int attendedSessions = (int) attendance.stream()
                    .map(StudentAttendanceHistoryDto::getStatus)
                    .filter(ReportingService::isAttendedStatus)
                    .count();
            int missedSessions = (int) attendance.stream()
                    .map(StudentAttendanceHistoryDto::getStatus)
                    .filter(ReportingService::isAbsentStatus)
                    .count();

            summary.setTotalSessions(totalSessions);
            summary.setRecordedSessions(recordedSessions);
            summary.setAttendedSessions(attendedSessions);
            summary.setMissedSessions(missedSessions);
            summary.setAttendanceRate(recordedSessions > 0 ? (double) attendedSessions / recordedSessions : 0.0);

            OffsetDateTime last = attendance.stream()
                    .map(StudentAttendanceHistoryDto::getMarkedAt)
                    .filter(Objects::nonNull)
                    .max(OffsetDateTime::compareTo)
                    .orElse(base.getLastAttendanceAt());
            summary.setLastAttendanceAt(last);
        } else {
            int totalSessions = sections.stream().mapToInt(StudentSectionReportDto::getTotalSessions).sum();
            int recordedSessions = sections.stream().mapToInt(StudentSectionReportDto::getRecordedSessions).sum();
            int attendedSessions = sections.stream().mapToInt(StudentSectionReportDto::getAttendedSessions).sum();
            int missedSessions = sections.stream().mapToInt(StudentSectionReportDto::getMissedSessions).sum();

            summary.setTotalSessions(totalSessions);
            summary.setRecordedSessions(recordedSessions);
            summary.setAttendedSessions(attendedSessions);
            summary.setMissedSessions(missedSessions);
            summary.setAttendanceRate(recordedSessions > 0 ? (double) attendedSessions / recordedSessions : 0.0);
            summary.setLastAttendanceAt(base.getLastAttendanceAt());
        }

        return summary;
    }

    private byte[] buildSectionWorkbook(List<SectionAttendanceExportRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Attendance");
            createHeaderRow(sheet, workbook, List.of(
                    "Student Name", "Student Number", "Email", "Course", "Section", "Session Date", "Start Time",
                    "End Time", "Status", "Marked At", "Marking Method", "Location", "Notes"));
            int rowIndex = 1;
            for (SectionAttendanceExportRow row : rows) {
                Row sheetRow = sheet.createRow(rowIndex++);
                writeCell(sheetRow, 0, row.getStudentName());
                writeCell(sheetRow, 1, row.getStudentNumber());
                writeCell(sheetRow, 2, row.getStudentEmail());
                writeCell(sheetRow, 3, row.getCourseCode());
                writeCell(sheetRow, 4, row.getSectionCode());
                writeCell(sheetRow, 5, row.getSessionDate() != null ? DATE_FORMAT.format(row.getSessionDate()) : "");
                writeCell(sheetRow, 6, formatTime(row.getStartTime()));
                writeCell(sheetRow, 7, formatTime(row.getEndTime()));
                writeCell(sheetRow, 8, normalizeStatus(row.getStatus()));
                writeCell(sheetRow, 9, formatTimestamp(row.getMarkedAt()));
                writeCell(sheetRow, 10, normalizeMarkingMethod(row.getMarkingMethod()));
                writeCell(sheetRow, 11, row.getLocation());
                writeCell(sheetRow, 12, row.getNotes());
            }
            autosizeColumns(sheet, 13);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate section workbook", ex);
        }
    }

    private String buildSectionCsv(List<SectionAttendanceExportRow> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("Student Name,Student Number,Email,Course,Section,Session Date,Start Time,End Time,Status,Marked At,Marking Method,Location,Notes\n");
        for (SectionAttendanceExportRow row : rows) {
            builder.append(csv(row.getStudentName())).append(',')
                    .append(csv(row.getStudentNumber())).append(',')
                    .append(csv(row.getStudentEmail())).append(',')
                    .append(csv(row.getCourseCode())).append(',')
                    .append(csv(row.getSectionCode())).append(',')
                    .append(csv(row.getSessionDate() != null ? DATE_FORMAT.format(row.getSessionDate()) : ""))
                    .append(',').append(csv(formatTime(row.getStartTime())))
                    .append(',').append(csv(formatTime(row.getEndTime())))
                    .append(',').append(csv(normalizeStatus(row.getStatus())))
                    .append(',').append(csv(formatTimestamp(row.getMarkedAt())))
                    .append(',').append(csv(normalizeMarkingMethod(row.getMarkingMethod())))
                    .append(',').append(csv(row.getLocation()))
                    .append(',').append(csv(row.getNotes()))
                    .append('\n');
        }
        return builder.toString();
    }

    private byte[] buildStudentWorkbook(StudentReportDetailDto detail) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet summarySheet = workbook.createSheet("Summary");
            createHeaderRow(summarySheet, workbook, List.of("Metric", "Value"));
            Row row1 = summarySheet.createRow(1);
            writeCell(row1, 0, "Attendance Rate");
            writeCell(row1, 1, percentage(detail.getSummary().getAttendanceRate()));
            Row row2 = summarySheet.createRow(2);
            writeCell(row2, 0, "Sections");
            writeCell(row2, 1, String.valueOf(detail.getSummary().getSectionCount()));
            Row row3 = summarySheet.createRow(3);
            writeCell(row3, 0, "Courses");
            writeCell(row3, 1, String.valueOf(detail.getSummary().getCourseCount()));
            autosizeColumns(summarySheet, 2);

            Sheet sectionsSheet = workbook.createSheet("Sections");
            createHeaderRow(sectionsSheet, workbook, List.of(
                    "Course", "Section", "Total Sessions", "Recorded Sessions", "Attended", "Missed", "Attendance Rate"));
            int idx = 1;
            for (StudentSectionReportDto section : detail.getSections()) {
                Row sectionRow = sectionsSheet.createRow(idx++);
                writeCell(sectionRow, 0, section.getCourseCode());
                writeCell(sectionRow, 1, section.getSectionCode());
                writeCell(sectionRow, 2, String.valueOf(section.getTotalSessions()));
                writeCell(sectionRow, 3, String.valueOf(section.getRecordedSessions()));
                writeCell(sectionRow, 4, String.valueOf(section.getAttendedSessions()));
                writeCell(sectionRow, 5, String.valueOf(section.getMissedSessions()));
                writeCell(sectionRow, 6, percentage(section.getAttendanceRate()));
            }
            autosizeColumns(sectionsSheet, 7);

            Sheet historySheet = workbook.createSheet("Attendance");
            createHeaderRow(historySheet, workbook, List.of(
                    "Course", "Section", "Session Date", "Start Time", "End Time", "Status", "Marked At", "Marking Method", "Location", "Notes"));
            int historyIdx = 1;
            for (StudentAttendanceHistoryDto attendance : detail.getAttendanceHistory()) {
                Row historyRow = historySheet.createRow(historyIdx++);
                writeCell(historyRow, 0, attendance.getCourseCode());
                writeCell(historyRow, 1, attendance.getSectionCode());
                writeCell(historyRow, 2, attendance.getSessionDate() != null ? DATE_FORMAT.format(attendance.getSessionDate()) : "");
                writeCell(historyRow, 3, formatTime(attendance.getStartTime()));
                writeCell(historyRow, 4, formatTime(attendance.getEndTime()));
                writeCell(historyRow, 5, normalizeStatus(attendance.getStatus()));
                writeCell(historyRow, 6, formatTimestamp(attendance.getMarkedAt()));
                writeCell(historyRow, 7, normalizeMarkingMethod(attendance.getMarkingMethod()));
                writeCell(historyRow, 8, attendance.getLocation());
                writeCell(historyRow, 9, attendance.getNotes());
            }
            autosizeColumns(historySheet, 10);

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate student workbook", ex);
        }
    }

    private String buildStudentCsv(StudentReportDetailDto detail) {
        StringBuilder builder = new StringBuilder();
        builder.append("Course,Section,Session Date,Start Time,End Time,Status,Marked At,Marking Method,Location,Notes\n");
        for (StudentAttendanceHistoryDto attendance : detail.getAttendanceHistory()) {
            builder.append(csv(attendance.getCourseCode())).append(',')
                    .append(csv(attendance.getSectionCode())).append(',')
                    .append(csv(attendance.getSessionDate() != null ? DATE_FORMAT.format(attendance.getSessionDate()) : ""))
                    .append(',').append(csv(formatTime(attendance.getStartTime())))
                    .append(',').append(csv(formatTime(attendance.getEndTime())))
                    .append(',').append(csv(normalizeStatus(attendance.getStatus())))
                    .append(',').append(csv(formatTimestamp(attendance.getMarkedAt())))
                    .append(',').append(csv(normalizeMarkingMethod(attendance.getMarkingMethod())))
                    .append(',').append(csv(attendance.getLocation()))
                    .append(',').append(csv(attendance.getNotes()))
                    .append('\n');
        }
        return builder.toString();
    }

    private ProfessorSectionReportDto toSectionSummary(SectionAttendanceExportRow metadata, SectionAnalyticsDto analytics) {
        ProfessorSectionReportDto dto = new ProfessorSectionReportDto();
        dto.setSectionId(metadata.getSectionId());
        dto.setCourseId(metadata.getCourseId());
        dto.setCourseCode(metadata.getCourseCode());
        dto.setCourseTitle(metadata.getCourseTitle());
        dto.setSectionCode(metadata.getSectionCode());
        dto.setLocation(metadata.getLocation());
        dto.setEnrolledStudents(metadata.getEnrolledStudents() != null ? metadata.getEnrolledStudents() : 0);
        dto.setMaxStudents(metadata.getMaxStudents() != null ? metadata.getMaxStudents() : 0);
        dto.setTotalSessions(analytics.getTotalSessions());
        dto.setCompletedSessions(analytics.getCompletedSessions());
        dto.setUpcomingSessions(analytics.getUpcomingSessions());
        dto.setAverageAttendanceRate(analytics.getAveragePresentRate());
        dto.setAveragePresentRate(analytics.getAveragePresentRate());
        dto.setAverageLateRate(analytics.getAverageLateRate());
        return dto;
    }

    private ProfessorSectionReportDto toSectionSummary(AdminSectionSummaryDto section, SectionAnalyticsDto analytics) {
        ProfessorSectionReportDto dto = new ProfessorSectionReportDto();
        dto.setSectionId(section.getSectionId());
        dto.setCourseId(section.getCourseId());
        dto.setCourseCode(section.getCourseCode());
        dto.setCourseTitle(section.getCourseTitle());
        dto.setSectionCode(section.getSectionCode());
        dto.setLocation(section.getLocation());
        dto.setEnrolledStudents(section.getEnrolledCount() != null ? section.getEnrolledCount() : 0);
        dto.setMaxStudents(section.getMaxStudents() != null ? section.getMaxStudents() : 0);
        dto.setTotalSessions(analytics.getTotalSessions());
        dto.setCompletedSessions(analytics.getCompletedSessions());
        dto.setUpcomingSessions(analytics.getUpcomingSessions());
        dto.setAverageAttendanceRate(analytics.getAveragePresentRate());
        dto.setAveragePresentRate(analytics.getAveragePresentRate());
        dto.setAverageLateRate(analytics.getAverageLateRate());
        return dto;
    }

    private String buildSectionFilename(SectionAttendanceExportRow metadata) {
        String course = Objects.requireNonNullElse(metadata.getCourseCode(), "course");
        String section = Objects.requireNonNullElse(metadata.getSectionCode(), "section");
        return String.format(Locale.ENGLISH, "%s-%s-attendance", sanitizeFileSegment(course), sanitizeFileSegment(section));
    }

    private String buildStudentFilename(ProfessorStudentReportDto summary) {
        String name = summary.getStudent() != null ? summary.getStudent().getFullName() : "student";
        return String.format(Locale.ENGLISH, "%s-attendance", sanitizeFileSegment(name));
    }

    private static void createHeaderRow(Sheet sheet, XSSFWorkbook workbook, List<String> headers) {
        Row header = sheet.createRow(0);
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(style);
        }
    }

    private static void writeCell(Row row, int index, String value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value != null ? value : "");
    }

    private static void autosizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\"", "\"\"");
        return '"' + sanitized + '"';
    }

    private static String formatTime(java.time.temporal.TemporalAccessor value) {
        if (value == null) {
            return "";
        }
        try {
            if (value instanceof java.time.OffsetDateTime offsetDateTime) {
                return TIME_FORMAT.format(offsetDateTime.withOffsetSameInstant(java.time.ZoneOffset.UTC));
            }
            return TIME_FORMAT.format(value);
        } catch (Exception ex) {
            return value.toString();
        }
    }

    private static String formatTimestamp(OffsetDateTime value) {
        if (value == null) {
            return "";
        }
        return value.atZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime().toString();
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            return "Pending";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "present" -> "Present";
            case "late" -> "Late";
            case "absent" -> "Absent";
            default -> capitalize(status);
        };
    }

    private static String normalizeMarkingMethod(String method) {
        if (method == null) {
            return "Manual";
        }
        return switch (method.toLowerCase(Locale.ROOT)) {
            case "auto" -> "Automatic";
            case "manual" -> "Manual";
            default -> capitalize(method);
        };
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static boolean hasStatus(String status) {
        return status != null && !status.isBlank();
    }

    private static boolean isAttendedStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toLowerCase(Locale.ROOT);
        return "present".equals(normalized) || "late".equals(normalized);
    }

    private static boolean isAbsentStatus(String status) {
        return status != null && "absent".equalsIgnoreCase(status);
    }

    private static String percentage(double value) {
        return String.format(Locale.ENGLISH, "%.1f%%", value * 100);
    }

    private static String sanitizeFileSegment(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static String sanitize(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    public enum ExportFormat {
        CSV,
        XLSX;

        public static ExportFormat fromString(String value) {
            if (value == null) {
                return XLSX;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "csv" -> CSV;
                case "xlsx" -> XLSX;
                default -> XLSX;
            };
        }
    }
}