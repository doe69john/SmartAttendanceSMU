package com.smartattendance.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.smartattendance.supabase.dto.CreateSectionRequest;
import com.smartattendance.supabase.dto.SectionSummaryDto;

@Repository
public class SectionJdbcRepository {

    private static final RowMapper<SectionSummaryDto> SECTION_SUMMARY_MAPPER = SectionJdbcRepository::mapSectionSummary;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SectionJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SectionSummaryDto> findSectionsForProfessor(UUID professorId) {
        return searchSectionsForProfessor(professorId, null, null, null);
    }

    public List<SectionSummaryDto> searchSectionsForProfessor(UUID professorId,
                                                             String query,
                                                             Integer dayOfWeek,
                                                             UUID courseId) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id,
                       s.course_id,
                       s.section_code,
                       s.day_of_week,
                       s.start_time,
                       s.end_time,
                       s.location,
                       s.late_threshold_minutes,
                       s.max_students,
                       c.course_code,
                       c.course_title,
                       c.description AS course_description,
                       COUNT(CASE WHEN se.is_active THEN se.id END) AS enrolled_count
                  FROM sections s
                  JOIN courses c ON c.id = s.course_id
                  LEFT JOIN student_enrollments se ON se.section_id = s.id
                 WHERE s.professor_id = :prof
                   AND s.is_active = true
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("prof", professorId);

        Integer normalizedDay = toDatabaseDayOfWeek(dayOfWeek);
        if (normalizedDay != null) {
            sql.append("                   AND s.day_of_week = :dayOfWeek\n");
            params.addValue("dayOfWeek", normalizedDay);
        }
        if (courseId != null) {
            sql.append("                   AND s.course_id = :courseId\n");
            params.addValue("courseId", courseId);
        }
        if (query != null && !query.isBlank()) {
            String normalized = "%" + query.toLowerCase(Locale.ROOT) + "%";
            sql.append("                   AND (LOWER(c.course_code) LIKE :query\n")
               .append("                        OR LOWER(c.course_title) LIKE :query\n")
               .append("                        OR LOWER(s.section_code) LIKE :query)\n");
            params.addValue("query", normalized);
        }

        sql.append("                 GROUP BY s.id, c.course_code, c.course_title, c.description\n");
        sql.append("                 ORDER BY c.course_code, s.section_code\n");
        return jdbcTemplate.query(sql.toString(), params, SECTION_SUMMARY_MAPPER);
    }

    public List<SectionSummaryDto> findSectionsForStudent(UUID studentId) {
        String sql = """
                SELECT s.id,
                       s.course_id,
                       s.section_code,
                       s.day_of_week,
                       s.start_time,
                       s.end_time,
                       s.location,
                       s.late_threshold_minutes,
                       s.max_students,
                       c.course_code,
                       c.course_title,
                       c.description AS course_description,
                       COUNT(CASE WHEN se.is_active THEN se.id END) AS enrolled_count
                  FROM sections s
                  JOIN courses c ON c.id = s.course_id
                  JOIN student_enrollments se ON se.section_id = s.id
                 WHERE se.student_id = :student
                   AND se.is_active = true
                   AND s.is_active = true
                 GROUP BY s.id, c.course_code, c.course_title, c.description
                 ORDER BY c.course_code, s.section_code
                """;
        return jdbcTemplate.query(sql, Map.of("student", studentId), SECTION_SUMMARY_MAPPER);
    }

    public List<SectionSummaryDto> listSections(Boolean active, UUID courseId, UUID professorId) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id,
                       s.course_id,
                       s.section_code,
                       s.day_of_week,
                       s.start_time,
                       s.end_time,
                       s.location,
                       s.late_threshold_minutes,
                       s.max_students,
                       c.course_code,
                       c.course_title,
                       c.description AS course_description,
                       COUNT(CASE WHEN se.is_active THEN se.id END) AS enrolled_count
                  FROM sections s
                  JOIN courses c ON c.id = s.course_id
                  LEFT JOIN student_enrollments se ON se.section_id = s.id
                 WHERE 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (active != null) {
            sql.append("                   AND s.is_active = :active\n");
            params.addValue("active", active);
        }
        if (courseId != null) {
            sql.append("                   AND s.course_id = :courseId\n");
            params.addValue("courseId", courseId);
        }
        if (professorId != null) {
            sql.append("                   AND s.professor_id = :professorId\n");
            params.addValue("professorId", professorId);
        }
        sql.append("                 GROUP BY s.id, c.course_code, c.course_title, c.description\n");
        sql.append("                 ORDER BY c.course_code, s.section_code\n");
        return jdbcTemplate.query(sql.toString(), params, SECTION_SUMMARY_MAPPER);
    }

    public SectionSummaryDto findSection(UUID sectionId) {
        String sql = """
                SELECT s.id,
                       s.course_id,
                       s.section_code,
                       s.day_of_week,
                       s.start_time,
                       s.end_time,
                       s.location,
                       s.late_threshold_minutes,
                       s.max_students,
                       c.course_code,
                       c.course_title,
                       c.description AS course_description,
                       COALESCE(enrolled.count, 0) AS enrolled_count
                  FROM sections s
                  JOIN courses c ON c.id = s.course_id
                  LEFT JOIN (
                        SELECT section_id, COUNT(*) AS count
                          FROM student_enrollments
                         WHERE is_active = true
                         GROUP BY section_id
                  ) enrolled ON enrolled.section_id = s.id
                 WHERE s.id = :id
                """;
        return jdbcTemplate.queryForObject(sql, Map.of("id", sectionId), SECTION_SUMMARY_MAPPER);
    }

    public boolean sectionCodeExistsForCourse(UUID courseId, String sectionCode, UUID excludeSectionId) {
        if (courseId == null || sectionCode == null) {
            return false;
        }
        StringBuilder sql = new StringBuilder("SELECT EXISTS(SELECT 1 FROM sections WHERE course_id = :courseId AND UPPER(section_code) = :code");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("code", sectionCode.toUpperCase(Locale.ROOT));
        if (excludeSectionId != null) {
            sql.append(" AND id <> :excludeId");
            params.addValue("excludeId", excludeSectionId);
        }
        sql.append(")");
        Boolean exists = jdbcTemplate.queryForObject(sql.toString(), params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public SectionSummaryDto createSection(UUID sectionId, UUID professorId, CreateSectionRequest request) {
        String sql = """
                INSERT INTO sections (id, course_id, section_code, professor_id, day_of_week, start_time, end_time, location, late_threshold_minutes, max_students, is_active)
                VALUES (:id, :course, :code, :professor, :day, :start, :end, :location, :late, :max, true)
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", sectionId)
                .addValue("course", request.getCourseId())
                .addValue("code", request.getSectionCode())
                .addValue("professor", professorId)
                .addValue("day", toDatabaseDayOfWeek(request.getDayOfWeek()))
                .addValue("start", parseTime(request.getStartTime()))
                .addValue("end", parseTime(request.getEndTime()))
                .addValue("location", request.getLocation())
                .addValue("late", normalizeLateThreshold(request.getLateThresholdMinutes()))
                .addValue("max", request.getMaxStudents() != null ? request.getMaxStudents() : 50));
        return findSection(sectionId);
    }

    public SectionSummaryDto updateSection(UUID sectionId, CreateSectionRequest request) {
        String sql = """
                UPDATE sections
                   SET course_id = :course,
                       section_code = :code,
                       day_of_week = :day,
                       start_time = :start,
                       end_time = :end,
                       location = :location,
                       late_threshold_minutes = :late,
                       max_students = :max
                 WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", sectionId)
                .addValue("course", request.getCourseId())
                .addValue("code", request.getSectionCode())
                .addValue("day", toDatabaseDayOfWeek(request.getDayOfWeek()))
                .addValue("start", parseTime(request.getStartTime()))
                .addValue("end", parseTime(request.getEndTime()))
                .addValue("location", request.getLocation())
                .addValue("late", normalizeLateThreshold(request.getLateThresholdMinutes()))
                .addValue("max", request.getMaxStudents() != null ? request.getMaxStudents() : 50));
        return findSection(sectionId);
    }

    public SectionSummaryDto updateSection(UUID sectionId, CreateSectionRequest request, UUID professorId) {
        String sql = """
                UPDATE sections
                   SET course_id = :course,
                       section_code = :code,
                       professor_id = :professor,
                       day_of_week = :day,
                       start_time = :start,
                       end_time = :end,
                       location = :location,
                       late_threshold_minutes = :late,
                       max_students = :max
                 WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", sectionId)
                .addValue("course", request.getCourseId())
                .addValue("code", request.getSectionCode())
                .addValue("professor", professorId)
                .addValue("day", toDatabaseDayOfWeek(request.getDayOfWeek()))
                .addValue("start", parseTime(request.getStartTime()))
                .addValue("end", parseTime(request.getEndTime()))
                .addValue("location", request.getLocation())
                .addValue("late", normalizeLateThreshold(request.getLateThresholdMinutes()))
                .addValue("max", request.getMaxStudents() != null ? request.getMaxStudents() : 50));
        return findSection(sectionId);
    }

    public void deleteSection(UUID sectionId) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", sectionId);

        jdbcTemplate.update("""
                DELETE FROM attendance_records
                 WHERE session_id IN (
                       SELECT id FROM attendance_sessions WHERE section_id = :id)
                """, params);
        jdbcTemplate.update("DELETE FROM attendance_sessions WHERE section_id = :id", params);
        jdbcTemplate.update("DELETE FROM student_enrollments WHERE section_id = :id", params);
        jdbcTemplate.update("DELETE FROM sections WHERE id = :id", params);
    }

    private static SectionSummaryDto mapSectionSummary(ResultSet rs, int rowNum) throws SQLException {
        SectionSummaryDto dto = new SectionSummaryDto();
        dto.setId((UUID) rs.getObject("id"));
        dto.setCourseId((UUID) rs.getObject("course_id"));
        dto.setSectionCode(rs.getString("section_code"));
        dto.setCourseCode(rs.getString("course_code"));
        dto.setCourseTitle(rs.getString("course_title"));
        dto.setDayOfWeek(toIsoDayOfWeek(rs.getInt("day_of_week")));
        dto.setStartTime(rs.getObject("start_time", LocalTime.class));
        dto.setEndTime(rs.getObject("end_time", LocalTime.class));
        dto.setLocation(rs.getString("location"));
        dto.setMaxStudents(rs.getInt("max_students"));
        dto.setEnrolledCount(rs.getInt("enrolled_count"));
        dto.setLateThresholdMinutes(rs.getObject("late_threshold_minutes", Integer.class));
        populateDerivedFields(dto);
        return dto;
    }

    private static Integer normalizeLateThreshold(Integer raw) {
        if (raw == null) {
            return 15;
        }
        int sanitized = Math.max(0, raw);
        return Math.min(sanitized, 240);
    }

    private static Integer toDatabaseDayOfWeek(Integer apiDay) {
        if (apiDay == null) {
            return null;
        }
        int normalized = Math.floorMod(apiDay, 7);
        return normalized;
    }

    private static int toIsoDayOfWeek(int databaseDay) {
        int normalized = Math.floorMod(databaseDay, 7);
        return normalized == 0 ? 7 : normalized;
    }

    private static LocalTime parseTime(String raw) {
        return raw != null ? LocalTime.parse(raw) : null;
    }

    private static void populateDerivedFields(SectionSummaryDto dto) {
        if (dto == null) {
            return;
        }

        int dayValue = dto.getDayOfWeek();
        if (dayValue >= 1 && dayValue <= 7) {
            DayOfWeek day = DayOfWeek.of(dayValue);
            dto.setDayLabel(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }

        LocalTime start = dto.getStartTime();
        LocalTime end = dto.getEndTime();
        if (start != null) {
            StringBuilder range = new StringBuilder(TIME_FORMATTER.format(start));
            if (end != null) {
                range.append(" - ").append(TIME_FORMATTER.format(end));
            }
            dto.setTimeRangeLabel(range.toString());
        }

        int enrolled = dto.getEnrolledCount();
        int capacity = dto.getMaxStudents();
        if (capacity > 0) {
            dto.setEnrollmentSummary(String.format(Locale.ENGLISH, "%d/%d seats", enrolled, capacity));
        } else {
            dto.setEnrollmentSummary(String.format(Locale.ENGLISH, "%d students", enrolled));
        }
    }
}
