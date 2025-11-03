package com.smartattendance.supabase.service.attendance;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartattendance.supabase.dto.AttendanceUpsertRequest;
import com.smartattendance.supabase.dto.PagedResponse;
import com.smartattendance.supabase.dto.SessionAttendanceRecord;
import com.smartattendance.supabase.dto.StudentDto;
import com.smartattendance.supabase.dto.events.AttendanceEvent;
import com.smartattendance.supabase.entity.AttendanceRecordEntity;
import com.smartattendance.supabase.entity.AttendanceSessionEntity;
import com.smartattendance.supabase.entity.CourseEntity;
import com.smartattendance.supabase.entity.SectionEntity;
import com.smartattendance.supabase.repository.AttendanceRecordRepository;
import com.smartattendance.supabase.repository.AttendanceSessionRepository;
import com.smartattendance.supabase.repository.CourseRepository;
import com.smartattendance.supabase.repository.SectionRepository;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.smartattendance.supabase.service.profile.StudentDirectoryService;
import com.smartattendance.supabase.service.session.SessionEventPublisher;

@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 200;

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final StudentDirectoryService studentDirectoryService;
    private final SessionEventPublisher eventPublisher;
    private final AttendanceSessionRepository sessionRepository;
    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;

    public AttendanceService(AttendanceRecordRepository attendanceRecordRepository,
            StudentDirectoryService studentDirectoryService,
            SessionEventPublisher eventPublisher,
            AttendanceSessionRepository sessionRepository,
            SectionRepository sectionRepository,
            CourseRepository courseRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.studentDirectoryService = studentDirectoryService;
        this.eventPublisher = eventPublisher;
        this.sessionRepository = sessionRepository;
        this.sectionRepository = sectionRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<SessionAttendanceRecord> search(UUID sessionId,
            UUID sectionId,
            UUID studentId,
            Collection<String> statusFilter,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer page,
            Integer size) {

        Pageable pageable = buildPageRequest(page, size);
        Set<AttendanceRecordEntity.Status> statuses = normaliseStatuses(statusFilter);

        Specification<AttendanceRecordEntity> specification = buildSearchSpecification(
                sessionId,
                sectionId,
                studentId,
                statuses,
                from,
                to);

        Page<AttendanceRecordEntity> result = attendanceRecordRepository.findAll(specification, pageable);

        List<AttendanceRecordEntity> records = result.getContent();
        Map<UUID, StudentDto> students = studentDirectoryService.findByIds(records.stream()
                .map(AttendanceRecordEntity::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        Map<UUID, AttendanceSessionEntity> sessions = indexById(
                sessionRepository.findAllById(records.stream()
                        .map(AttendanceRecordEntity::getSessionId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()),
                AttendanceSessionEntity::getId);

        Map<UUID, SectionEntity> sections = indexById(
                sectionRepository.findAllById(sessions.values().stream()
                        .map(AttendanceSessionEntity::getSectionId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()),
                SectionEntity::getId);

        Map<UUID, CourseEntity> courses = indexById(
                courseRepository.findAllById(sections.values().stream()
                        .map(SectionEntity::getCourseId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()),
                CourseEntity::getId);

        List<SessionAttendanceRecord> items = records.stream()
                .map(record -> {
                    AttendanceSessionEntity session = sessions.get(record.getSessionId());
                    SectionEntity section = session != null ? sections.get(session.getSectionId()) : null;
                    CourseEntity course = section != null ? courses.get(section.getCourseId()) : null;
                    return toDto(record, students.get(record.getStudentId()), session, section, course);
                })
                .toList();

        return new PagedResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public SessionAttendanceRecord upsert(AttendanceUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        AttendanceRecordEntity entity = resolveTargetRecord(request);
        applyUpdates(entity, request);
        AttendanceRecordEntity saved = attendanceRecordRepository.save(entity);
        updateRoster(saved);
        publishAttendanceEvent(saved);
        StudentDto student = studentDirectoryService.findById(saved.getStudentId()).orElse(null);
        AttendanceSessionEntity session = resolveSession(saved.getSessionId());
        SectionEntity section = resolveSection(session);
        CourseEntity course = resolveCourse(section);
        return toDto(saved, student, session, section, course);
    }

    @Transactional
    public SessionAttendanceRecord update(UUID recordId, AttendanceUpsertRequest request) {
        if (recordId == null) {
            throw new IllegalArgumentException("recordId must not be null");
        }
        AttendanceRecordEntity entity = attendanceRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Attendance record not found: " + recordId));
        applyUpdates(entity, request);
        AttendanceRecordEntity saved = attendanceRecordRepository.save(entity);
        updateRoster(saved);
        publishAttendanceEvent(saved);
        StudentDto student = studentDirectoryService.findById(saved.getStudentId()).orElse(null);
        AttendanceSessionEntity session = resolveSession(saved.getSessionId());
        SectionEntity section = resolveSection(session);
        CourseEntity course = resolveCourse(section);
        return toDto(saved, student, session, section, course);
    }

    private void applyUpdates(AttendanceRecordEntity entity, AttendanceUpsertRequest request) {
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (entity.getId() == null) {
            entity.setId(Optional.ofNullable(request.getId()).orElseGet(UUID::randomUUID));
        }
        if (request.getSessionId() != null) {
            entity.setSessionId(request.getSessionId());
        }
        if (request.getStudentId() != null) {
            entity.setStudentId(request.getStudentId());
        }
        AttendanceRecordEntity.Status status = parseStatus(request.getStatus()).orElse(null);
        if (status != null) {
            entity.setStatus(status);
            entity.setMarkedAt(now);
        }
        AttendanceRecordEntity.MarkingMethod markingMethod = parseMarkingMethod(request.getMarkingMethod()).orElse(null);
        if (markingMethod != null) {
            entity.setMarkingMethod(markingMethod);
        }
        if (request.getConfidenceScore() != null) {
            entity.setConfidenceScore(request.getConfidenceScore());
        }
        if (request.getNotes() != null) {
            entity.setNotes(request.getNotes());
        }
        entity.setUpdatedAt(now);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        if (entity.getSessionId() == null || entity.getStudentId() == null) {
            throw new IllegalArgumentException("sessionId and studentId must be provided");
        }
    }

    private AttendanceRecordEntity resolveTargetRecord(AttendanceUpsertRequest request) {
        if (request.getId() != null) {
            return attendanceRecordRepository.findById(request.getId())
                    .orElseGet(() -> createFreshRecord(request.getId(), request.getSessionId(), request.getStudentId()));
        }
        UUID sessionId = request.getSessionId();
        UUID studentId = request.getStudentId();
        if (sessionId != null && studentId != null) {
            return attendanceRecordRepository.findBySessionIdAndStudentId(sessionId, studentId)
                    .orElseGet(() -> createFreshRecord(null, sessionId, studentId));
        }
        return createFreshRecord(null, sessionId, studentId);
    }

    private AttendanceRecordEntity createFreshRecord(UUID id, UUID sessionId, UUID studentId) {
        AttendanceRecordEntity entity = new AttendanceRecordEntity();
        entity.setId(id != null ? id : UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setStudentId(studentId);
        return entity;
    }

    private Pageable buildPageRequest(Integer page, Integer size) {
        int pageIndex = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        Sort sort = Sort.by(Sort.Order.desc("markedAt"), Sort.Order.desc("updatedAt"));
        return PageRequest.of(pageIndex, pageSize, sort);
    }

    private Set<AttendanceRecordEntity.Status> normaliseStatuses(Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Set.of();
        }
        EnumSet<AttendanceRecordEntity.Status> parsed = EnumSet.noneOf(AttendanceRecordEntity.Status.class);
        for (String value : statuses) {
            parseStatus(value).ifPresent(parsed::add);
        }
        return parsed;
    }

    private Specification<AttendanceRecordEntity> buildSearchSpecification(UUID sessionId,
            UUID sectionId,
            UUID studentId,
            Set<AttendanceRecordEntity.Status> statuses,
            OffsetDateTime from,
            OffsetDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sessionId != null) {
                predicates.add(cb.equal(root.get("sessionId"), sessionId));
            }
            if (sectionId != null) {
                Subquery<UUID> sessionIds = query.subquery(UUID.class);
                Root<AttendanceSessionEntity> sessionRoot = sessionIds.from(AttendanceSessionEntity.class);
                sessionIds.select(sessionRoot.get("id"));
                sessionIds.where(cb.equal(sessionRoot.get("sectionId"), sectionId));
                predicates.add(root.get("sessionId").in(sessionIds));
            }
            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("markedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("markedAt"), to));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Optional<AttendanceRecordEntity.Status> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        try {
            return Optional.of(AttendanceRecordEntity.Status.valueOf(normalised));
        } catch (IllegalArgumentException ex) {
            log.debug("Ignoring unknown attendance status '{}': {}", value, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AttendanceRecordEntity.MarkingMethod> parseMarkingMethod(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        try {
            return Optional.of(AttendanceRecordEntity.MarkingMethod.valueOf(normalised));
        } catch (IllegalArgumentException ex) {
            log.debug("Ignoring unknown marking method '{}': {}", value, ex.getMessage());
            return Optional.empty();
        }
    }

    private SessionAttendanceRecord toDto(AttendanceRecordEntity entity,
            StudentDto student,
            AttendanceSessionEntity session,
            SectionEntity section,
            CourseEntity course) {
        SessionAttendanceRecord dto = new SessionAttendanceRecord();
        dto.setId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setStudentId(entity.getStudentId());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        dto.setConfidenceScore(entity.getConfidenceScore());
        dto.setMarkingMethod(entity.getMarkingMethod() != null ? entity.getMarkingMethod().name() : null);
        dto.setMarkedAt(entity.getMarkedAt());
        dto.setLastSeen(entity.getLastSeen());
        dto.setNotes(entity.getNotes());
        dto.setStudent(student);
        if (session != null) {
            dto.setSectionId(session.getSectionId());
        }
        if (section != null) {
            dto.setSectionCode(section.getSectionCode());
            dto.setCourseId(section.getCourseId());
        }
        if (course != null) {
            dto.setCourseCode(course.getCourseCode());
            dto.setCourseTitle(course.getCourseTitle());
        }
        return dto;
    }

    private AttendanceSessionEntity resolveSession(UUID sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessionRepository.findById(sessionId).orElse(null);
    }

    private SectionEntity resolveSection(AttendanceSessionEntity session) {
        if (session == null || session.getSectionId() == null) {
            return null;
        }
        return sectionRepository.findById(session.getSectionId()).orElse(null);
    }

    private CourseEntity resolveCourse(SectionEntity section) {
        if (section == null || section.getCourseId() == null) {
            return null;
        }
        return courseRepository.findById(section.getCourseId()).orElse(null);
    }

    private <T, K> Map<K, T> indexById(Iterable<T> entities, Function<T, K> keyExtractor) {
        return StreamSupport.stream(entities.spliterator(), false)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(keyExtractor, Function.identity(), (left, right) -> left));
    }

    private void updateRoster(AttendanceRecordEntity record) {
        // Legacy session_rosters table has been removed; SessionLifecycleService now
        // ensures attendance_records exist per enrollee during session activation.
    }

    private void publishAttendanceEvent(AttendanceRecordEntity record) {
        if (record.getSessionId() == null || record.getStudentId() == null) {
            return;
        }
        String status = record.getStatus() != null ? record.getStatus().name().toLowerCase(Locale.ROOT) : null;
        AttendanceEvent event = new AttendanceEvent(
                record.getStudentId(),
                status,
                record.getConfidenceScore(),
                record.getMarkedAt());
        eventPublisher.publish(record.getSessionId(), "attendance", event);
    }
}
