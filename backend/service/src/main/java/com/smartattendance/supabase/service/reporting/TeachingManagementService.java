package com.smartattendance.supabase.service.reporting;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smartattendance.supabase.dto.CourseSummaryDto;
import com.smartattendance.supabase.dto.CreateCourseRequest;
import com.smartattendance.supabase.dto.CreateSectionRequest;
import com.smartattendance.supabase.dto.ScheduleSessionRequest;
import com.smartattendance.supabase.dto.SectionSummaryDto;
import com.smartattendance.supabase.dto.SessionSummaryDto;
import com.smartattendance.supabase.dto.StudentDto;
import com.smartattendance.supabase.dto.ProfessorDirectoryEntry;
import com.smartattendance.supabase.dto.StudentAttendanceHistoryDto;
import com.smartattendance.supabase.dto.StudentSectionDetailDto;
import com.smartattendance.supabase.entity.StudentEnrollmentEntity;
import com.smartattendance.supabase.entity.AttendanceRecordEntity;
import com.smartattendance.supabase.repository.StudentEnrollmentRepository;
import com.smartattendance.supabase.repository.CourseJdbcRepository;
import com.smartattendance.supabase.repository.EnrollmentJdbcRepository;
import com.smartattendance.supabase.repository.SectionJdbcRepository;
import com.smartattendance.supabase.repository.ProfessorJdbcRepository;
import com.smartattendance.supabase.repository.AttendanceRecordRepository;
import com.smartattendance.supabase.service.profile.StudentDirectoryService;

@Service
public class TeachingManagementService {

    private static final long EARLY_START_MINUTES = 30L;
    private static final long DEFAULT_SESSION_WINDOW_HOURS = 4L;
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter FLEXIBLE_LOCAL_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("H:mm")
            .optionalStart()
            .appendPattern("[:ss]")
            .optionalEnd()
            .toFormatter();

    private static final List<String> ALLOWED_SECTION_CODES = IntStream.rangeClosed(1, 15)
            .mapToObj(i -> "G" + i)
            .collect(Collectors.toUnmodifiableList());
    private static final Set<String> ALLOWED_SECTION_CODE_SET = new LinkedHashSet<>(ALLOWED_SECTION_CODES);
    private static final String SECTION_CODE_RANGE_MESSAGE =
            "Section code must be one of " + String.join(", ", ALLOWED_SECTION_CODES);

    private final CourseJdbcRepository courseRepository;
    private final SectionJdbcRepository sectionRepository;
    private final EnrollmentJdbcRepository enrollmentJdbcRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentDirectoryService studentDirectoryService;
    private final ProfessorJdbcRepository professorJdbcRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    public TeachingManagementService(CourseJdbcRepository courseRepository,
                                     SectionJdbcRepository sectionRepository,
                                     EnrollmentJdbcRepository enrollmentJdbcRepository,
                                     StudentEnrollmentRepository enrollmentRepository,
                                     StudentDirectoryService studentDirectoryService,
                                     ProfessorJdbcRepository professorJdbcRepository,
                                     AttendanceRecordRepository attendanceRecordRepository) {
        this.courseRepository = courseRepository;
        this.sectionRepository = sectionRepository;
        this.enrollmentJdbcRepository = enrollmentJdbcRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentDirectoryService = studentDirectoryService;
        this.professorJdbcRepository = professorJdbcRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<SectionSummaryDto> findSectionsForProfessor(UUID professorId) {
        return sectionRepository.findSectionsForProfessor(professorId);
    }

    @Transactional(readOnly = true)
    public List<SectionSummaryDto> searchSectionsForProfessor(UUID professorId,
                                                              String query,
                                                              Integer dayOfWeek,
                                                              UUID courseId) {
        String sanitizedQuery = query != null ? query.trim() : null;
        return sectionRepository.searchSectionsForProfessor(professorId, sanitizedQuery, dayOfWeek, courseId);
    }

    @Transactional(readOnly = true)
    public List<SectionSummaryDto> findSectionsForStudent(UUID studentId) {
        return sectionRepository.findSectionsForStudent(studentId);
    }

    @Transactional(readOnly = true)
    public StudentSectionDetailDto loadStudentSectionDetail(UUID studentId, UUID sectionId) {
        if (studentId == null || sectionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId and sectionId are required");
        }

        StudentEnrollmentEntity enrollment = enrollmentRepository.findBySectionIdAndStudentId(sectionId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));
        if (!Boolean.TRUE.equals(enrollment.getActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment is inactive");
        }

        SectionSummaryDto section = sectionRepository.findSection(sectionId);
        if (section == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found");
        }

        SectionSummaryDto sanitized = sanitizeSectionForStudent(section);
        List<SessionSummaryDto> sessions = enrollmentJdbcRepository.findSessionsForSection(sectionId);

        List<StudentAttendanceHistoryDto> history = new ArrayList<>();
        for (SessionSummaryDto session : sessions) {
            StudentAttendanceHistoryDto dto = new StudentAttendanceHistoryDto();
            dto.setSessionId(session.getId());
            dto.setSectionId(sectionId);
            dto.setSectionCode(section.getSectionCode());
            dto.setCourseCode(section.getCourseCode());
            dto.setCourseTitle(section.getCourseTitle());
            dto.setSessionDate(session.getSessionDate());
            dto.setStartTime(session.getStartTime());
            dto.setEndTime(session.getEndTime());
            dto.setLocation(session.getLocation());

            attendanceRecordRepository.findBySessionIdAndStudentId(session.getId(), studentId)
                    .ifPresentOrElse(record -> populateAttendance(dto, record), () -> dto.setStatus("pending"));

            history.add(dto);
        }

        StudentSectionDetailDto detail = new StudentSectionDetailDto();
        detail.setSection(sanitized);
        detail.setAttendanceHistory(history);
        return detail;
    }

    @Transactional(readOnly = true)
    public List<SectionSummaryDto> listSections(Boolean active, UUID courseId, UUID professorId) {
        return sectionRepository.listSections(active, courseId, professorId);
    }

    @Transactional(readOnly = true)
    public List<CourseSummaryDto> findActiveCourses() {
        return courseRepository.findActiveCourses();
    }

    @Transactional(readOnly = true)
    public List<CourseSummaryDto> findCoursesForProfessor(UUID professorId) {
        return courseRepository.findCoursesForProfessor(professorId);
    }

    @Transactional(readOnly = true)
    public SectionSummaryDto findSection(UUID sectionId) {
        return sectionRepository.findSection(sectionId);
    }

    @Transactional(readOnly = true)
    public List<SessionSummaryDto> findSessionsForSection(UUID sectionId) {
        return enrollmentJdbcRepository.findSessionsForSection(sectionId);
    }

    @Transactional(readOnly = true)
    public List<SessionSummaryDto> findSessionsForProfessor(UUID professorId) {
        return enrollmentJdbcRepository.findSessionsForProfessor(professorId);
    }

    @Transactional(readOnly = true)
    public List<StudentDto> listSectionRoster(UUID sectionId) {
        return buildRoster(sectionId);
    }

    @Transactional(readOnly = true)
    public List<StudentDto> searchStudents(String query, Integer limit) {
        int resolvedLimit = limit != null ? Math.max(1, Math.min(limit, 50)) : 20;
        return studentDirectoryService.searchStudents(query, resolvedLimit);
    }

    @Transactional
    public List<StudentDto> upsertSectionEnrollments(UUID sectionId, List<UUID> studentIds, Boolean activate) {
        if (studentIds == null || studentIds.isEmpty()) {
            return buildRoster(sectionId);
        }

        if (Boolean.FALSE.equals(activate)) {
            List<StudentEnrollmentEntity> toDelete = new ArrayList<>();
            for (UUID studentId : studentIds) {
                if (studentId == null) {
                    continue;
                }
                enrollmentRepository.findBySectionIdAndStudentId(sectionId, studentId)
                        .ifPresent(toDelete::add);
            }
            if (!toDelete.isEmpty()) {
                enrollmentRepository.deleteAll(toDelete);
            }
            return buildRoster(sectionId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<StudentEnrollmentEntity> toPersist = new ArrayList<>();
        for (UUID studentId : studentIds) {
            if (studentId == null) {
                continue;
            }
            StudentEnrollmentEntity entity = enrollmentRepository
                    .findBySectionIdAndStudentId(sectionId, studentId)
                    .orElseGet(() -> {
                        StudentEnrollmentEntity fresh = new StudentEnrollmentEntity();
                        fresh.setId(UUID.randomUUID());
                        fresh.setSectionId(sectionId);
                        fresh.setStudentId(studentId);
                        return fresh;
                    });
            entity.setActive(Boolean.TRUE);
            entity.setEnrolledAt(now);
            toPersist.add(entity);
        }
        if (!toPersist.isEmpty()) {
            enrollmentRepository.saveAll(toPersist);
        }
        return buildRoster(sectionId);
    }

    @Transactional
    public CourseSummaryDto createCourse(CreateCourseRequest request) {
        UUID id = UUID.randomUUID();
        return courseRepository.createCourse(id, request);
    }

    @Transactional
    public SectionSummaryDto createSection(UUID professorId, CreateSectionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        UUID courseId = request.getCourseId();
        if (courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId is required");
        }
        String normalizedCode = normalizeSectionCode(request.getSectionCode());
        if (normalizedCode.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sectionCode is required");
        }
        if (!ALLOWED_SECTION_CODE_SET.contains(normalizedCode)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, SECTION_CODE_RANGE_MESSAGE);
        }
        request.setSectionCode(normalizedCode);
        validateSectionSchedule(request);
        ensureSectionCodeAvailable(courseId, normalizedCode, null);
        UUID id = UUID.randomUUID();
        SectionSummaryDto section;
        try {
            section = sectionRepository.createSection(id, professorId, request);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, duplicateSectionMessage(), ex);
        }
        if (request.getStudentIds() != null && !request.getStudentIds().isEmpty()) {
            upsertSectionEnrollments(id, request.getStudentIds(), Boolean.TRUE);
        }
        return section;
    }

    @Transactional
    public SectionSummaryDto createSectionWithProfessor(CreateSectionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        UUID professorId = request.getProfessorId();
        if (professorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "professorId is required");
        }
        if (!professorJdbcRepository.professorExists(professorId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Professor not found");
        }
        return createSection(professorId, request);
    }

    @Transactional
    public CourseSummaryDto updateCourse(UUID courseId, CreateCourseRequest request) {
        if (courseId == null) {
            throw new IllegalArgumentException("courseId is required");
        }
        return courseRepository.updateCourse(courseId, request);
    }

    @Transactional
    public void deleteCourse(UUID courseId) {
        if (courseId == null) {
            return;
        }
        courseRepository.deleteCourse(courseId);
    }

    @Transactional
    public SectionSummaryDto updateSection(UUID sectionId, CreateSectionRequest request) {
        return updateSectionInternal(sectionId, request, false);
    }

    @Transactional
    public SectionSummaryDto updateSectionWithProfessor(UUID sectionId, CreateSectionRequest request) {
        return updateSectionInternal(sectionId, request, true);
    }

    @Transactional
    public void deleteSection(UUID sectionId) {
        if (sectionId == null) {
            return;
        }
        sectionRepository.deleteSection(sectionId);
    }

    private SectionSummaryDto updateSectionInternal(UUID sectionId,
                                                    CreateSectionRequest request,
                                                    boolean allowProfessorChange) {
        if (sectionId == null) {
            throw new IllegalArgumentException("sectionId is required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        UUID courseId = request.getCourseId();
        if (courseId == null) {
            SectionSummaryDto existing = sectionRepository.findSection(sectionId);
            courseId = existing != null ? existing.getCourseId() : null;
            request.setCourseId(courseId);
        }
        if (courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courseId is required");
        }
        String normalizedCode = normalizeSectionCode(request.getSectionCode());
        if (normalizedCode.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sectionCode is required");
        }
        if (!ALLOWED_SECTION_CODE_SET.contains(normalizedCode)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, SECTION_CODE_RANGE_MESSAGE);
        }
        request.setSectionCode(normalizedCode);
        validateSectionSchedule(request);
        ensureSectionCodeAvailable(courseId, normalizedCode, sectionId);

        UUID professorId = null;
        if (allowProfessorChange) {
            professorId = request.getProfessorId();
            if (professorId != null && !professorJdbcRepository.professorExists(professorId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Professor not found");
            }
        }

        try {
            if (allowProfessorChange) {
                return sectionRepository.updateSection(sectionId, request, professorId);
            }
            return sectionRepository.updateSection(sectionId, request);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, duplicateSectionMessage(), ex);
        }
    }

    @Transactional
    public SessionSummaryDto scheduleSession(UUID professorId, UUID sectionId, ScheduleSessionRequest request) {
        SectionSummaryDto section = null;
        try {
            section = sectionRepository.findSection(sectionId);
        } catch (EmptyResultDataAccessException ignored) {
            // If a section cannot be loaded we still fall back to the legacy behaviour of creating a session
        }

        LocalDate sessionDate = parseSessionDate(request.getSessionDate());
        OffsetDateTime requestedStart = resolveRequestedStart(sessionDate, request.getStartTime(), section);
        OffsetDateTime windowStart = computeWindowStart(sessionDate, requestedStart, section);
        OffsetDateTime windowEnd = computeWindowEnd(sessionDate, requestedStart, section, windowStart);

        SessionSummaryDto existing = enrollmentJdbcRepository.findSessionInWindow(sectionId, professorId, sessionDate, windowStart, windowEnd);
        if (existing != null) {
            return existing;
        }

        UUID id = UUID.randomUUID();
        return enrollmentJdbcRepository.scheduleSession(id, professorId, sectionId, request, requestedStart);
    }

    private LocalDate parseSessionDate(String rawDate) {
        if (rawDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionDate is required");
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sessionDate format", ex);
        }
    }

    private OffsetDateTime resolveRequestedStart(LocalDate sessionDate,
                                                String rawStart,
                                                SectionSummaryDto section) {
        if (rawStart == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime is required");
        }
        try {
            return OffsetDateTime.parse(rawStart, ISO_OFFSET);
        } catch (DateTimeParseException ex) {
            LocalTime local = parseLocalStart(rawStart);
            ZoneOffset offset = resolveOffset(sessionDate, local, section);
            return OffsetDateTime.of(LocalDateTime.of(sessionDate, local), offset);
        }
    }

    private LocalTime parseLocalStart(String rawStart) {
        String trimmed = rawStart != null ? rawStart.trim() : "";
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid startTime format");
        }
        try {
            return LocalTime.parse(trimmed, FLEXIBLE_LOCAL_TIME);
        } catch (DateTimeParseException primary) {
            try {
                return LocalDateTime.parse(trimmed).toLocalTime();
            } catch (DateTimeParseException secondary) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid startTime format", secondary);
            }
        }
    }

    private ZoneOffset resolveOffset(LocalDate sessionDate, LocalTime time, SectionSummaryDto section) {
        ZoneId zone = ZoneId.systemDefault();
        LocalTime referenceTime = time;
        if (section != null && section.getStartTime() != null) {
            referenceTime = section.getStartTime();
        }
        LocalDateTime reference = LocalDateTime.of(sessionDate, referenceTime);
        return zone.getRules().getOffset(reference);
    }

    private OffsetDateTime computeWindowStart(LocalDate sessionDate,
                                              OffsetDateTime requestedStart,
                                              SectionSummaryDto section) {
        if (section != null && section.getStartTime() != null) {
            OffsetDateTime scheduledStart = alignToSessionDate(sessionDate, section.getStartTime(), requestedStart.getOffset());
            return scheduledStart.minusMinutes(EARLY_START_MINUTES);
        }
        return requestedStart.minusMinutes(EARLY_START_MINUTES);
    }

    private OffsetDateTime computeWindowEnd(LocalDate sessionDate,
                                            OffsetDateTime requestedStart,
                                            SectionSummaryDto section,
                                            OffsetDateTime windowStart) {
        OffsetDateTime candidate;
        if (section != null && section.getEndTime() != null) {
            candidate = alignToSessionDate(sessionDate, section.getEndTime(), requestedStart.getOffset());
            if (section.getStartTime() != null && !section.getEndTime().isAfter(section.getStartTime())) {
                candidate = candidate.plusDays(1);
            }
        } else if (section != null && section.getStartTime() != null) {
            candidate = alignToSessionDate(sessionDate, section.getStartTime(), requestedStart.getOffset())
                    .plusHours(DEFAULT_SESSION_WINDOW_HOURS);
        } else {
            candidate = requestedStart.plusHours(DEFAULT_SESSION_WINDOW_HOURS);
        }

        if (candidate.isBefore(windowStart)) {
            return windowStart.plusHours(DEFAULT_SESSION_WINDOW_HOURS);
        }
        return candidate;
    }

    private OffsetDateTime alignToSessionDate(LocalDate sessionDate, LocalTime time, ZoneOffset offset) {
        return OffsetDateTime.of(LocalDateTime.of(sessionDate, time), offset);
    }

    @Transactional(readOnly = true)
    public List<ProfessorDirectoryEntry> listProfessors(Boolean active) {
        return professorJdbcRepository.listProfessors(active);
    }

    private SectionSummaryDto sanitizeSectionForStudent(SectionSummaryDto section) {
        SectionSummaryDto sanitized = new SectionSummaryDto();
        sanitized.setId(section.getId());
        sanitized.setCourseId(section.getCourseId());
        sanitized.setCourseCode(section.getCourseCode());
        sanitized.setCourseTitle(section.getCourseTitle());
        sanitized.setCourseDescription(section.getCourseDescription());
        sanitized.setSectionCode(section.getSectionCode());
        sanitized.setDayOfWeek(section.getDayOfWeek());
        sanitized.setStartTime(section.getStartTime());
        sanitized.setEndTime(section.getEndTime());
        sanitized.setLocation(section.getLocation());
        sanitized.setDayLabel(section.getDayLabel());
        sanitized.setTimeRangeLabel(section.getTimeRangeLabel());
        sanitized.setLateThresholdMinutes(section.getLateThresholdMinutes());
        sanitized.setEnrollmentSummary(null);
        sanitized.setMaxStudents(0);
        sanitized.setEnrolledCount(0);
        return sanitized;
    }

    private void populateAttendance(StudentAttendanceHistoryDto dto, AttendanceRecordEntity record) {
        if (record.getStatus() != null) {
            dto.setStatus(record.getStatus().name().toLowerCase(Locale.ROOT));
        } else {
            dto.setStatus(null);
        }
        dto.setMarkedAt(record.getMarkedAt());
        if (record.getMarkingMethod() != null) {
            dto.setMarkingMethod(record.getMarkingMethod().name().toLowerCase(Locale.ROOT));
        } else {
            dto.setMarkingMethod(null);
        }
        dto.setNotes(record.getNotes());
    }

    private static String normalizeSectionCode(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = raw.replaceAll("\\s+", "");
        if (stripped.isEmpty()) {
            return "";
        }
        return stripped.toUpperCase(Locale.ROOT);
    }

    private void validateSectionSchedule(CreateSectionRequest request) {
        if (request == null) {
            return;
        }
        LocalTime start = null;
        String rawStart = request.getStartTime();
        if (rawStart != null) {
            String trimmed = rawStart.trim();
            if (!trimmed.isEmpty()) {
                try {
                    start = LocalTime.parse(trimmed, FLEXIBLE_LOCAL_TIME);
                } catch (DateTimeParseException ex) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid startTime format", ex);
                }
            }
        }
        LocalTime end = null;
        String rawEnd = request.getEndTime();
        if (rawEnd != null) {
            String trimmed = rawEnd.trim();
            if (!trimmed.isEmpty()) {
                try {
                    end = LocalTime.parse(trimmed, FLEXIBLE_LOCAL_TIME);
                } catch (DateTimeParseException ex) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid endTime format", ex);
                }
            }
        }
        if (start != null && end != null && !start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must be earlier than endTime");
        }
        if (start != null) {
            request.setStartTime(start.toString());
        }
        if (end != null) {
            request.setEndTime(end.toString());
        }
        Integer maxStudents = request.getMaxStudents();
        if (maxStudents != null && maxStudents <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxStudents must be greater than zero");
        }
    }

    private void ensureSectionCodeAvailable(UUID courseId, String sectionCode, UUID excludeSectionId) {
        if (courseId == null || sectionCode == null) {
            return;
        }
        boolean exists = sectionRepository.sectionCodeExistsForCourse(courseId, sectionCode, excludeSectionId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, duplicateSectionMessage());
        }
    }

    private static String duplicateSectionMessage() {
        return "A section with this code already exists for the selected course";
    }

    private List<StudentDto> buildRoster(UUID sectionId) {
        List<StudentEnrollmentEntity> enrollments = enrollmentRepository.findBySectionIdAndActiveTrue(sectionId);
        List<UUID> studentIds = enrollments.stream()
                .map(StudentEnrollmentEntity::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return studentDirectoryService.listByIds(studentIds);
    }
}
