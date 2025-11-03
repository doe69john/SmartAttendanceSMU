package com.smartattendance.supabase.service.session;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartattendance.supabase.dto.RecognitionLogEntryDto;
import com.smartattendance.supabase.dto.SectionAnalyticsDto;
import com.smartattendance.supabase.dto.SessionAttendanceRecord;
import com.smartattendance.supabase.dto.SessionAttendanceStatsDto;
import com.smartattendance.supabase.dto.SessionDetailsDto;
import com.smartattendance.supabase.dto.SessionSummaryDto;
import com.smartattendance.supabase.dto.StudentDto;
import com.smartattendance.supabase.entity.AttendanceRecordEntity;
import com.smartattendance.supabase.entity.AttendanceSessionEntity;
import com.smartattendance.supabase.entity.CourseEntity;
import com.smartattendance.supabase.entity.SectionEntity;
import com.smartattendance.supabase.entity.StudentEnrollmentEntity;
import com.smartattendance.supabase.repository.AttendanceRecordRepository;
import com.smartattendance.supabase.repository.AttendanceSessionRepository;
import com.smartattendance.supabase.repository.CourseRepository;
import com.smartattendance.supabase.repository.EnrollmentJdbcRepository;
import com.smartattendance.supabase.repository.SectionRepository;
import com.smartattendance.supabase.repository.StudentEnrollmentRepository;
import com.smartattendance.supabase.service.session.SessionMapper;
import com.smartattendance.supabase.service.profile.StudentDirectoryService;
import com.smartattendance.supabase.service.system.SystemLogService;

@Service
public class SessionQueryService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final StudentDirectoryService studentDirectoryService;
    private final SystemLogService systemLogService;
    private final SessionMapper sessionMapper;
    private final EnrollmentJdbcRepository enrollmentJdbcRepository;
    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;

    public SessionQueryService(AttendanceSessionRepository sessionRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            StudentEnrollmentRepository enrollmentRepository,
            StudentDirectoryService studentDirectoryService,
            SystemLogService systemLogService,
            SessionMapper sessionMapper,
            EnrollmentJdbcRepository enrollmentJdbcRepository,
            SectionRepository sectionRepository,
            CourseRepository courseRepository) {
        this.sessionRepository = sessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentDirectoryService = studentDirectoryService;
        this.systemLogService = systemLogService;
        this.sessionMapper = sessionMapper;
        this.enrollmentJdbcRepository = enrollmentJdbcRepository;
        this.sectionRepository = sectionRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public SessionDetailsDto loadSession(UUID sessionId) {
        AttendanceSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        return sessionMapper.toDetailsDto(session);
    }

    @Transactional(readOnly = true)
    public List<SessionAttendanceRecord> loadAttendance(UUID sessionId) {
        List<AttendanceRecordEntity> records = attendanceRecordRepository.findBySessionIdOrderByMarkedAtDesc(sessionId);
        AttendanceSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        SectionEntity section = session != null ? sectionRepository.findById(session.getSectionId()).orElse(null) : null;
        CourseEntity course = section != null ? courseRepository.findById(section.getCourseId()).orElse(null) : null;
        List<UUID> studentIds = records.stream()
                .map(AttendanceRecordEntity::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, StudentDto> students = loadStudentMap(studentIds);
        return records.stream()
                .map(record -> toAttendanceView(record, students.get(record.getStudentId()), session, section, course))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StudentDto> loadSectionStudents(UUID sectionId) {
        List<StudentEnrollmentEntity> enrollments = enrollmentRepository.findBySectionIdAndActiveTrue(sectionId);
        List<UUID> studentIds = enrollments.stream()
                .map(StudentEnrollmentEntity::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return studentDirectoryService.listByIds(studentIds);
    }

    @Transactional(readOnly = true)
    public List<StudentDto> loadSessionStudents(UUID sessionId) {
        AttendanceSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        return loadSectionStudents(session.getSectionId());
    }

    @Transactional(readOnly = true)
    public SectionAnalyticsDto loadSectionAnalytics(UUID sectionId) {
        List<SessionSummaryDto> sessions = enrollmentJdbcRepository.findSessionsForSection(sectionId);
        SectionAnalyticsDto analytics = new SectionAnalyticsDto();
        analytics.setTotalSessions(sessions.size());

        int completed = 0;
        int upcoming = 0;
        double presentSum = 0.0;
        double lateSum = 0.0;
        int rateSamples = 0;

        for (SessionSummaryDto session : sessions) {
            String status = session.getStatus();
            boolean includeInAverage = false;
            if (status != null) {
                String normalized = status.toLowerCase(Locale.ROOT);
                if ("completed".equals(normalized)) {
                    completed++;
                    includeInAverage = true;
                } else if ("active".equals(normalized)) {
                    includeInAverage = true;
                } else if ("scheduled".equals(normalized)) {
                    upcoming++;
                }
            }

            int denominator = session.getTotalStudents() > 0 ? session.getTotalStudents() : session.getRecordedStudents();
            if (includeInAverage && denominator > 0) {
                presentSum += session.getPresentRate();
                lateSum += session.getLateRate();
                rateSamples++;
            }
        }

        analytics.setCompletedSessions(completed);
        analytics.setUpcomingSessions(upcoming);
        double avgPresent = rateSamples > 0 ? presentSum / rateSamples : 0.0;
        double avgLate = rateSamples > 0 ? lateSum / rateSamples : 0.0;
        analytics.setAveragePresentRate(avgPresent);
        analytics.setAverageLateRate(avgLate);
        analytics.setAverageAttendanceRate(avgPresent);
        return analytics;
    }

    @Transactional(readOnly = true)
    public SessionAttendanceStatsDto loadAttendanceStats(UUID sessionId) {
        List<AttendanceRecordEntity> records = attendanceRecordRepository.findBySessionIdOrderByMarkedAtDesc(sessionId);
        SessionAttendanceStatsDto stats = new SessionAttendanceStatsDto();

        int present = 0;
        int late = 0;
        int absent = 0;
        int pending = 0;
        int automatic = 0;
        int manual = 0;

        for (AttendanceRecordEntity record : records) {
            AttendanceRecordEntity.Status status = record.getStatus();
            if (status != null) {
                switch (status) {
                    case present -> present++;
                    case late -> late++;
                    case absent -> absent++;
                    default -> pending++;
                }
            } else {
                pending++;
            }

            AttendanceRecordEntity.MarkingMethod method = record.getMarkingMethod();
            if (method == AttendanceRecordEntity.MarkingMethod.auto) {
                automatic++;
            } else {
                manual++;
            }
        }

        stats.setTotal(records.size());
        stats.setPresent(present);
        stats.setLate(late);
        stats.setAbsent(absent);
        stats.setPending(pending);
        stats.setAutomatic(automatic);
        stats.setManual(manual);
        return stats;
    }

    @Transactional(readOnly = true)
    public List<RecognitionLogEntryDto> loadRecognitionLog(UUID sessionId, Integer limit) {
        if (sessionId == null) {
            return List.of();
        }
        int max = (limit != null && limit > 0) ? limit : 100;
        return systemLogService.fetchRecognitionLog(sessionId, max);
    }

    private Map<UUID, StudentDto> loadStudentMap(List<UUID> studentIds) {
        return studentDirectoryService.findByIds(studentIds);
    }

    private SessionAttendanceRecord toAttendanceView(AttendanceRecordEntity record,
            StudentDto student,
            AttendanceSessionEntity session,
            SectionEntity section,
            CourseEntity course) {
        SessionAttendanceRecord view = new SessionAttendanceRecord();
        view.setId(record.getId());
        view.setSessionId(record.getSessionId());
        view.setStudentId(record.getStudentId());
        view.setStatus(record.getStatus() != null ? record.getStatus().name() : null);
        view.setConfidenceScore(record.getConfidenceScore());
        view.setMarkedAt(record.getMarkedAt());
        view.setMarkingMethod(record.getMarkingMethod() != null ? record.getMarkingMethod().name() : null);
        view.setLastSeen(record.getLastSeen());
        view.setNotes(record.getNotes());
        view.setStudent(student);
        if (session != null) {
            view.setSectionId(session.getSectionId());
        }
        if (section != null) {
            view.setSectionCode(section.getSectionCode());
            view.setCourseId(section.getCourseId());
        }
        if (course != null) {
            view.setCourseCode(course.getCourseCode());
            view.setCourseTitle(course.getCourseTitle());
        }
        return view;
    }

}