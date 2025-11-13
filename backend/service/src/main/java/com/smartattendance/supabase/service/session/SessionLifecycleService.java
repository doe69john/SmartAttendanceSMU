package com.smartattendance.supabase.service.session;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.smartattendance.supabase.dto.SessionDetailsDto;
import com.smartattendance.supabase.dto.events.AttendanceEvent;
import com.smartattendance.supabase.dto.events.SessionActionEvent;
import com.smartattendance.supabase.entity.AttendanceRecordEntity;
import com.smartattendance.supabase.entity.AttendanceSessionEntity;
import com.smartattendance.supabase.entity.StudentEnrollmentEntity;
import com.smartattendance.supabase.entity.SectionEntity;
import com.smartattendance.supabase.repository.AttendanceRecordRepository;
import com.smartattendance.supabase.repository.AttendanceSessionRepository;
import com.smartattendance.supabase.repository.StudentEnrollmentRepository;
import com.smartattendance.supabase.repository.SectionRepository;
import com.smartattendance.supabase.service.recognition.SectionModelService;
import com.smartattendance.supabase.service.recognition.SectionModelService.SectionModelTrainingException;
import com.smartattendance.supabase.service.recognition.SectionModelService.SectionRetrainResult;
import com.smartattendance.supabase.service.system.SystemLogService;
import com.smartattendance.supabase.service.companion.CompanionAccessTokenService;

@Service
public class SessionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SessionLifecycleService.class);

    private final AttendanceSessionRepository sessionRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final AttendanceRecordRepository recordRepository;
    private final SystemLogService systemLogService;
    private final SessionEventPublisher eventPublisher;
    private final SessionMapper sessionMapper;
    private final SectionModelService sectionModelService;
    private final SectionRepository sectionRepository;
    private final CompanionAccessTokenService companionTokenService;

    public SessionLifecycleService(
            AttendanceSessionRepository sessionRepository,
            StudentEnrollmentRepository enrollmentRepository,
            AttendanceRecordRepository recordRepository,
            SystemLogService systemLogService,
            SessionEventPublisher eventPublisher,
            SessionMapper sessionMapper,
            SectionModelService sectionModelService,
            SectionRepository sectionRepository,
            CompanionAccessTokenService companionTokenService) {
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.recordRepository = recordRepository;
        this.systemLogService = systemLogService;
        this.eventPublisher = eventPublisher;
        this.sessionMapper = sessionMapper;
        this.sectionModelService = sectionModelService;
        this.sectionRepository = sectionRepository;
        this.companionTokenService = companionTokenService;
    }

    public enum Action {
        start,
        stop,
        pause,
        resume
    }

    @Transactional
    public SessionDetailsDto handleSessionAction(UUID sessionId, Action action, UUID professorId) {
        log.info("Session {} action '{}' requested (professor={})", sessionId, action, professorId);
        AttendanceSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (professorId != null && !professorId.equals(session.getProfessorId())) {
            throw new IllegalArgumentException("Unauthorized: Not your session");
        }

        OffsetDateTime now = OffsetDateTime.now();
        session.setUpdatedAt(now);

        SectionEntity section = null;
        if (action == Action.start && session.getSectionId() != null) {
            section = sectionRepository.findById(session.getSectionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Section not found for session " + session.getId() + ": " + session.getSectionId()));
        }

        SectionRetrainResult retrainResult = null;
        try {
            switch (action) {
                case start -> {
                    ensureStartWindow(session, section, now);
                    retrainResult = retrainSectionForSession(session);
                    if (retrainResult != null) {
                        log.info("Session {} retrain completed: section={} images={} missingStudents={}",
                                session.getId(),
                                session.getSectionId(),
                                retrainResult.imageCount(),
                                retrainResult.missingStudentIds() != null ? retrainResult.missingStudentIds().size() : 0);
                    }
                    activateSession(session, now);
                }
                case resume -> resumeSession(session, now);
                case pause -> pauseSession(session, now);
                case stop -> {
                    completeSession(session, now);
                    companionTokenService.revokeTokensForSection(session.getSectionId());
                }
                default -> throw new IllegalStateException("Unexpected action: " + action);
            }
        } catch (RuntimeException ex) {
            log.error("Session {} action '{}' failed: {}", sessionId, action, ex.getMessage(), ex);
            throw ex;
        }

        AttendanceSessionEntity saved = sessionRepository.save(session);
        log.info("Session {} action '{}' updated status={}", saved.getId(), action, saved.getStatus());
        logSessionAction(saved, action, professorId, now);
        publishEvent(saved, action, now);
        return sessionMapper.toDetailsDto(saved, retrainResult);
    }

    private SectionRetrainResult retrainSectionForSession(AttendanceSessionEntity session) {
        if (session.getSectionId() == null) {
            throw new IllegalStateException("Session " + session.getId() + " is missing a section reference");
        }
        sectionRepository.findById(session.getSectionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Section not found for session " + session.getId() + ": " + session.getSectionId()));
        try {
            return sectionModelService.retrainSectionSync(session.getSectionId());
        } catch (SectionModelTrainingException ex) {
            log.error("Retraining failed for session {} section {}: {} (missingStudents={})",
                    session.getId(),
                    session.getSectionId(),
                    ex.getMessage(),
                    ex.getMissingStudentIds() != null ? ex.getMissingStudentIds().size() : 0,
                    ex);
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private void activateSession(AttendanceSessionEntity session, OffsetDateTime now) {
        if (session.getStartTime() == null) {
            session.setStartTime(now);
        }
        session.setEndTime(null);
        session.setStatus(AttendanceSessionEntity.Status.active);
        ensureAttendanceRecords(session.getId(), session.getSectionId(), now);
    }

    private void resumeSession(AttendanceSessionEntity session, OffsetDateTime now) {
        session.setStatus(AttendanceSessionEntity.Status.active);
        session.setEndTime(null);
        if (session.getStartTime() == null) {
            session.setStartTime(now);
        }
        ensureAttendanceRecords(session.getId(), session.getSectionId(), now);
    }

    private void pauseSession(AttendanceSessionEntity session, OffsetDateTime now) {
        session.setStatus(AttendanceSessionEntity.Status.scheduled);
        session.setNotes("paused at " + now);
    }

    private void completeSession(AttendanceSessionEntity session, OffsetDateTime now) {
        session.setStatus(AttendanceSessionEntity.Status.completed);
        session.setEndTime(now);
    }

    private void ensureAttendanceRecords(UUID sessionId, UUID sectionId, OffsetDateTime now) {
        if (sessionId == null || sectionId == null) {
            return;
        }
        List<StudentEnrollmentEntity> enrollments = enrollmentRepository.findBySectionIdAndActiveTrue(sectionId);
        if (enrollments.isEmpty()) {
            return;
        }

        Map<UUID, AttendanceRecordEntity> existingRecords = new HashMap<>();
        for (AttendanceRecordEntity record : recordRepository.findBySessionId(sessionId)) {
            UUID studentId = record.getStudentId();
            if (studentId != null) {
                existingRecords.putIfAbsent(studentId, record);
            }
        }

        List<AttendanceRecordEntity> toPersist = new ArrayList<>();
        List<AttendanceEvent> events = new ArrayList<>();

        for (StudentEnrollmentEntity enrollment : enrollments) {
            UUID studentId = enrollment.getStudentId();
            if (studentId == null) {
                continue;
            }
            AttendanceRecordEntity record = existingRecords.get(studentId);
            boolean created = false;
            if (record == null) {
                record = new AttendanceRecordEntity();
                record.setId(UUID.randomUUID());
                record.setSessionId(sessionId);
                record.setStudentId(studentId);
                created = true;
            }

            AttendanceRecordEntity.Status status = record.getStatus();
            boolean pending = status == null || status == AttendanceRecordEntity.Status.pending;
            boolean needsAbsenceSeed = created || pending;
            boolean updated = false;

            if (needsAbsenceSeed) {
                record.setStatus(AttendanceRecordEntity.Status.absent);
                record.setMarkedAt(now);
                record.setMarkingMethod(AttendanceRecordEntity.MarkingMethod.auto);
                events.add(new AttendanceEvent(
                        studentId,
                        AttendanceRecordEntity.Status.absent.name().toLowerCase(Locale.ROOT),
                        null,
                        now));
                updated = true;
            } else {
                if (record.getStatus() == AttendanceRecordEntity.Status.absent && record.getMarkedAt() == null) {
                    record.setMarkedAt(now);
                    updated = true;
                }
                if (record.getMarkingMethod() == null) {
                    record.setMarkingMethod(AttendanceRecordEntity.MarkingMethod.auto);
                    updated = true;
                }
            }

            if (updated || created) {
                if (record.getCreatedAt() == null) {
                    record.setCreatedAt(now);
                }
                record.setUpdatedAt(now);
                toPersist.add(record);
            }
        }

        if (!toPersist.isEmpty()) {
            recordRepository.saveAll(toPersist);
        }
        for (AttendanceEvent event : events) {
            eventPublisher.publish(sessionId, "attendance", event);
        }
    }

    private void logSessionAction(AttendanceSessionEntity session, Action action, UUID professorId, OffsetDateTime now) {
        String status = session.getStatus() != null ? session.getStatus().name() : null;
        systemLogService.recordSessionAction(session.getId(), action.name(), professorId, status,
                session.getStartTime(), session.getEndTime(), now);
    }

    private void publishEvent(AttendanceSessionEntity session, Action action, OffsetDateTime now) {
        String status = session.getStatus() != null ? session.getStatus().name().toLowerCase(Locale.ROOT) : null;
        SessionActionEvent event = new SessionActionEvent(action.name(), status, now);
        eventPublisher.publish(session.getId(), "session-action", event);
    }

    private void ensureStartWindow(AttendanceSessionEntity session, SectionEntity section, OffsetDateTime now) {
        if (session == null || section == null) {
            return;
        }
        if (session.getSessionDate() == null || section.getStartTime() == null) {
            return;
        }
        ZoneOffset offset = now.getOffset();
        OffsetDateTime scheduledStart = OffsetDateTime.of(session.getSessionDate(), section.getStartTime(), offset);
        OffsetDateTime earliestAllowed = scheduledStart.minusMinutes(30);
        if (now.isBefore(earliestAllowed)) {
            String message = String.format(
                    "Live session can only start within 30 minutes of the scheduled start time (%s on %s)",
                    scheduledStart.toLocalTime(),
                    scheduledStart.toLocalDate());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
        }
    }

}

