package com.smartattendance.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.smartattendance.supabase.dto.ScheduleSessionRequest;
import com.smartattendance.supabase.dto.SessionSummaryDto;

@Repository
public class EnrollmentJdbcRepository {

    private static final RowMapper<SessionSummaryDto> SESSION_SUMMARY_MAPPER = EnrollmentJdbcRepository::mapSessionSummary;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final ZoneId CAMPUS_TIME_ZONE = ZoneId.of("Asia/Singapore");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EnrollmentJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SessionSummaryDto> findSessionsForSection(UUID sectionId) {
        String sql = """
                WITH roster AS (
                    SELECT section_id, COUNT(*) AS total_students
                      FROM student_enrollments
                     WHERE is_active = true
                     GROUP BY section_id
                ), attendance_stats AS (
                    SELECT session_id,
                           COUNT(*) AS recorded_students,
                           SUM(CASE WHEN status = 'present' THEN 1 ELSE 0 END) AS present_count,
                           SUM(CASE WHEN status = 'late' THEN 1 ELSE 0 END) AS late_count,
                           SUM(CASE WHEN status = 'absent' THEN 1 ELSE 0 END) AS absent_count
                      FROM attendance_records
                     GROUP BY session_id
                )
                SELECT sess.id,
                       sess.section_id,
                       sess.session_date,
                       sess.start_time,
                       sess.end_time,
                       sess.status,
                       sess.location,
                       sess.notes,
                       COALESCE(stats.recorded_students, 0) AS attendance_count,
                       COALESCE(stats.present_count, 0) AS present_count,
                       COALESCE(stats.late_count, 0) AS late_count,
                       COALESCE(stats.absent_count, 0) AS absent_count,
                       COALESCE(stats.recorded_students, 0) AS recorded_students,
                       COALESCE(stats.recorded_students, roster.total_students, 0) AS total_students
                  FROM attendance_sessions sess
                  LEFT JOIN attendance_stats stats ON stats.session_id = sess.id
                  LEFT JOIN roster ON roster.section_id = sess.section_id
                 WHERE sess.section_id = :section
                 ORDER BY sess.session_date DESC, sess.start_time DESC NULLS LAST
                """;
        return jdbcTemplate.query(sql, Map.of("section", sectionId), SESSION_SUMMARY_MAPPER);
    }

    public List<SessionSummaryDto> findSessionsForProfessor(UUID professorId) {
        String sql = """
                WITH roster AS (
                    SELECT section_id, COUNT(*) AS total_students
                      FROM student_enrollments
                     WHERE is_active = true
                     GROUP BY section_id
                ), attendance_stats AS (
                    SELECT session_id,
                           COUNT(*) AS recorded_students,
                           SUM(CASE WHEN status = 'present' THEN 1 ELSE 0 END) AS present_count,
                           SUM(CASE WHEN status = 'late' THEN 1 ELSE 0 END) AS late_count,
                           SUM(CASE WHEN status = 'absent' THEN 1 ELSE 0 END) AS absent_count
                      FROM attendance_records
                     GROUP BY session_id
                )
                SELECT sess.id,
                       sess.section_id,
                       sess.session_date,
                       sess.start_time,
                       sess.end_time,
                       sess.status,
                       sess.location,
                       sess.notes,
                       COALESCE(stats.recorded_students, 0) AS attendance_count,
                       COALESCE(stats.present_count, 0) AS present_count,
                       COALESCE(stats.late_count, 0) AS late_count,
                       COALESCE(stats.absent_count, 0) AS absent_count,
                       COALESCE(stats.recorded_students, 0) AS recorded_students,
                       COALESCE(stats.recorded_students, roster.total_students, 0) AS total_students
                  FROM attendance_sessions sess
                  LEFT JOIN attendance_stats stats ON stats.session_id = sess.id
                  LEFT JOIN roster ON roster.section_id = sess.section_id
                 WHERE sess.professor_id = :professor
                 ORDER BY sess.session_date DESC, sess.start_time DESC NULLS LAST
                """;
        return jdbcTemplate.query(sql, Map.of("professor", professorId), SESSION_SUMMARY_MAPPER);
    }

    public SessionSummaryDto scheduleSession(UUID sessionId,
                                            UUID professorId,
                                            UUID sectionId,
                                            ScheduleSessionRequest request,
                                            OffsetDateTime startTime) {
        String sql = """
                INSERT INTO attendance_sessions (id, section_id, professor_id, session_date, start_time, late_threshold_minutes, status, location, notes)
                VALUES (:id, :section, :professor, :date, :start, :late, 'scheduled', :location, :notes)
                """;
        LocalDate date = LocalDate.parse(request.getSessionDate());
        int lateThreshold = resolveLateThreshold(sectionId, request.getLateThresholdMinutes());
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", sessionId)
                .addValue("section", sectionId)
                .addValue("professor", professorId)
                .addValue("date", date)
                .addValue("start", startTime)
                .addValue("late", lateThreshold)
                .addValue("location", request.getLocation())
                .addValue("notes", request.getNotes()));
        return findSession(sessionId);
    }

    public SessionSummaryDto findSessionInWindow(UUID sectionId,
                                                UUID professorId,
                                                LocalDate sessionDate,
                                                OffsetDateTime windowStart,
                                                OffsetDateTime windowEnd) {
        if (sectionId == null || sessionDate == null || windowStart == null || windowEnd == null || windowEnd.isBefore(windowStart)) {
            return null;
        }

        String sql = """
                WITH roster AS (
                    SELECT section_id, COUNT(*) AS total_students
                      FROM student_enrollments
                     WHERE is_active = true
                     GROUP BY section_id
                ), attendance_stats AS (
                    SELECT session_id,
                           COUNT(*) AS recorded_students,
                           SUM(CASE WHEN status = 'present' THEN 1 ELSE 0 END) AS present_count,
                           SUM(CASE WHEN status = 'late' THEN 1 ELSE 0 END) AS late_count,
                           SUM(CASE WHEN status = 'absent' THEN 1 ELSE 0 END) AS absent_count
                      FROM attendance_records
                     GROUP BY session_id
                )
                SELECT sess.id,
                       sess.section_id,
                       sess.session_date,
                       sess.start_time,
                       sess.end_time,
                       sess.status,
                       sess.location,
                       sess.notes,
                       COALESCE(stats.recorded_students, 0) AS attendance_count,
                       COALESCE(stats.present_count, 0) AS present_count,
                       COALESCE(stats.late_count, 0) AS late_count,
                       COALESCE(stats.absent_count, 0) AS absent_count,
                       COALESCE(stats.recorded_students, 0) AS recorded_students,
                       COALESCE(stats.recorded_students, roster.total_students, 0) AS total_students
                  FROM attendance_sessions sess
                  LEFT JOIN attendance_stats stats ON stats.session_id = sess.id
                  LEFT JOIN roster ON roster.section_id = sess.section_id
                 WHERE sess.section_id = :section
                   AND sess.professor_id = :professor
                   AND sess.session_date = :date
                   AND sess.start_time <= :windowEnd
                   AND COALESCE(sess.end_time, sess.start_time) >= :windowStart
                 ORDER BY CASE sess.status
                             WHEN 'active' THEN 0
                             WHEN 'scheduled' THEN 1
                             WHEN 'completed' THEN 2
                             ELSE 3
                          END,
                          sess.start_time DESC NULLS LAST
                 LIMIT 1
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("section", sectionId)
                .addValue("professor", professorId)
                .addValue("date", sessionDate)
                .addValue("windowStart", windowStart)
                .addValue("windowEnd", windowEnd);

        List<SessionSummaryDto> sessions = jdbcTemplate.query(sql, params, SESSION_SUMMARY_MAPPER);
        if (sessions.isEmpty()) {
            return null;
        }
        return sessions.get(0);
    }

    public SessionSummaryDto findSession(UUID sessionId) {
        String sql = """
                WITH roster AS (
                    SELECT section_id, COUNT(*) AS total_students
                      FROM student_enrollments
                     WHERE is_active = true
                     GROUP BY section_id
                ), attendance_stats AS (
                    SELECT session_id,
                           COUNT(*) AS recorded_students,
                           SUM(CASE WHEN status = 'present' THEN 1 ELSE 0 END) AS present_count,
                           SUM(CASE WHEN status = 'late' THEN 1 ELSE 0 END) AS late_count,
                           SUM(CASE WHEN status = 'absent' THEN 1 ELSE 0 END) AS absent_count
                      FROM attendance_records
                     GROUP BY session_id
                )
                SELECT sess.id,
                       sess.section_id,
                       sess.session_date,
                       sess.start_time,
                       sess.end_time,
                       sess.status,
                       sess.location,
                       sess.notes,
                       COALESCE(stats.recorded_students, 0) AS attendance_count,
                       COALESCE(stats.present_count, 0) AS present_count,
                       COALESCE(stats.late_count, 0) AS late_count,
                       COALESCE(stats.absent_count, 0) AS absent_count,
                       COALESCE(stats.recorded_students, 0) AS recorded_students,
                       COALESCE(stats.recorded_students, roster.total_students, 0) AS total_students
                  FROM attendance_sessions sess
                  LEFT JOIN attendance_stats stats ON stats.session_id = sess.id
                  LEFT JOIN roster ON roster.section_id = sess.section_id
                 WHERE sess.id = :id
                """;
        return jdbcTemplate.queryForObject(sql, Map.of("id", sessionId), SESSION_SUMMARY_MAPPER);
    }

    private int resolveLateThreshold(UUID sectionId, Integer requested) {
        if (requested != null) {
            return sanitizeLateThreshold(requested);
        }
        if (sectionId == null) {
            return 15;
        }
        try {
            Integer stored = jdbcTemplate.queryForObject(
                    "SELECT late_threshold_minutes FROM sections WHERE id = :section",
                    new MapSqlParameterSource("section", sectionId),
                    Integer.class);
            if (stored == null) {
                return 15;
            }
            return sanitizeLateThreshold(stored);
        } catch (Exception ex) {
            return 15;
        }
    }

    private int sanitizeLateThreshold(Integer value) {
        if (value == null) {
            return 15;
        }
        int sanitized = Math.max(0, value);
        return Math.min(sanitized, 240);
    }

    private static SessionSummaryDto mapSessionSummary(ResultSet rs, int rowNum) throws SQLException {
        SessionSummaryDto dto = new SessionSummaryDto();
        dto.setId((UUID) rs.getObject("id"));
        dto.setSectionId((UUID) rs.getObject("section_id"));
        dto.setSessionDate(rs.getObject("session_date", LocalDate.class));
        dto.setStartTime(rs.getObject("start_time", OffsetDateTime.class));
        dto.setEndTime(rs.getObject("end_time", OffsetDateTime.class));
        dto.setStatus(rs.getString("status"));
        dto.setLocation(rs.getString("location"));
        dto.setNotes(rs.getString("notes"));
        dto.setAttendanceCount(rs.getInt("attendance_count"));
        dto.setPresentCount(rs.getInt("present_count"));
        dto.setLateCount(rs.getInt("late_count"));
        dto.setAbsentCount(rs.getInt("absent_count"));
        dto.setRecordedStudents(rs.getInt("recorded_students"));
        dto.setTotalStudents(rs.getInt("total_students"));
        enrichDerivedFields(dto);
        return dto;
    }

    private static void enrichDerivedFields(SessionSummaryDto dto) {
        if (dto == null) {
            return;
        }

        if (dto.getSessionDate() != null) {
            dto.setDayLabel(dto.getSessionDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }

        OffsetDateTime start = dto.getStartTime();
        OffsetDateTime end = dto.getEndTime();
        if (start != null) {
            ZonedDateTime localizedStart = start.atZoneSameInstant(CAMPUS_TIME_ZONE);
            StringBuilder range = new StringBuilder(TIME_FORMATTER.format(localizedStart));
            if (end != null) {
                ZonedDateTime localizedEnd = end.atZoneSameInstant(CAMPUS_TIME_ZONE);
                range.append(" - ").append(TIME_FORMATTER.format(localizedEnd));
            }
            dto.setTimeRangeLabel(range.toString());
        }

        int denominator = dto.getTotalStudents() > 0 ? dto.getTotalStudents() : dto.getRecordedStudents();
        int present = dto.getPresentCount();
        int late = dto.getLateCount();

        double presentRate = denominator > 0 ? (double) present / denominator : 0.0;
        double lateRate = denominator > 0 ? (double) late / denominator : 0.0;

        dto.setPresentRate(presentRate);
        dto.setLateRate(lateRate);
        dto.setAttendanceRate(presentRate);

        if (denominator > 0) {
            dto.setAttendanceSummary(String.format(Locale.ENGLISH,
                    "Present %d/%d • Late %d/%d", present, denominator, late, denominator));
        } else {
            dto.setAttendanceSummary(String.format(Locale.ENGLISH, "Present %d • Late %d", present, late));
        }
    }
}
