package com.smartattendance.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.smartattendance.supabase.dto.admin.AdminSectionSummaryDto;

@Repository
public class AdminSectionJdbcRepository {

    private static final RowMapper<AdminSectionSummaryDto> ADMIN_SECTION_MAPPER = AdminSectionJdbcRepository::mapSection;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminSectionJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminSectionSummaryDto> findSectionSummaries(String query) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id,
                       s.course_id,
                       s.section_code,
                       s.day_of_week,
                       s.start_time,
                       s.end_time,
                       s.location,
                       s.max_students,
                       s.late_threshold_minutes,
                       c.course_code,
                       c.course_title,
                       p.id AS professor_id,
                       p.full_name AS professor_name,
                       p.email AS professor_email,
                       p.staff_id AS professor_staff_id,
                       COUNT(DISTINCT CASE WHEN se.is_active THEN se.id END) AS enrolled_count
                  FROM sections s
                  JOIN courses c ON c.id = s.course_id
                  LEFT JOIN profiles p ON p.id = s.professor_id
                  LEFT JOIN student_enrollments se ON se.section_id = s.id
                 WHERE 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sanitized = normalizeQuery(query);
        if (sanitized != null) {
            sql.append("                   AND (c.course_code ILIKE :pattern\n")
                    .append("                        OR c.course_title ILIKE :pattern\n")
                    .append("                        OR s.section_code ILIKE :pattern\n")
                    .append("                        OR COALESCE(p.full_name, '') ILIKE :pattern\n")
                    .append("                        OR COALESCE(p.staff_id, '') ILIKE :pattern)\n");
            params.addValue("pattern", '%' + sanitized + '%');
        }
        sql.append("                 GROUP BY s.id, c.course_code, c.course_title, p.id, p.full_name, p.email, p.staff_id\n");
        sql.append("                 ORDER BY c.course_code, s.section_code\n");
        return jdbcTemplate.query(sql.toString(), params, ADMIN_SECTION_MAPPER);
    }

    public AdminSectionSummaryDto findSectionSummary(UUID sectionId) {
        String sql = """
                SELECT s.id,
                       s.course_id,
                       s.section_code,
                       s.day_of_week,
                       s.start_time,
                       s.end_time,
                       s.location,
                       s.max_students,
                       s.late_threshold_minutes,
                       c.course_code,
                       c.course_title,
                       p.id AS professor_id,
                       p.full_name AS professor_name,
                       p.email AS professor_email,
                       p.staff_id AS professor_staff_id,
                       COUNT(DISTINCT CASE WHEN se.is_active THEN se.id END) AS enrolled_count
                  FROM sections s
                  JOIN courses c ON c.id = s.course_id
                  LEFT JOIN profiles p ON p.id = s.professor_id
                  LEFT JOIN student_enrollments se ON se.section_id = s.id
                 WHERE s.id = :id
                 GROUP BY s.id, c.course_code, c.course_title, p.id, p.full_name, p.email, p.staff_id
                """;
        List<AdminSectionSummaryDto> results = jdbcTemplate.query(sql,
                new MapSqlParameterSource("id", sectionId), ADMIN_SECTION_MAPPER);
        return results.isEmpty() ? null : results.get(0);
    }

    private static AdminSectionSummaryDto mapSection(ResultSet rs, int rowNum) throws SQLException {
        AdminSectionSummaryDto dto = new AdminSectionSummaryDto();
        dto.setSectionId((UUID) rs.getObject("id"));
        dto.setCourseId((UUID) rs.getObject("course_id"));
        dto.setSectionCode(rs.getString("section_code"));
        dto.setCourseCode(rs.getString("course_code"));
        dto.setCourseTitle(rs.getString("course_title"));
        dto.setDayOfWeek(toIsoDayOfWeek(rs.getInt("day_of_week")));
        dto.setStartTime(rs.getObject("start_time", LocalTime.class));
        dto.setEndTime(rs.getObject("end_time", LocalTime.class));
        dto.setLocation(rs.getString("location"));
        dto.setMaxStudents(getInteger(rs, "max_students"));
        dto.setLateThresholdMinutes(getInteger(rs, "late_threshold_minutes"));
        dto.setEnrolledCount(getInteger(rs, "enrolled_count"));
        dto.setProfessorId((UUID) rs.getObject("professor_id"));
        dto.setProfessorName(rs.getString("professor_name"));
        dto.setProfessorEmail(rs.getString("professor_email"));
        dto.setProfessorStaffId(rs.getString("professor_staff_id"));
        populateDerivedFields(dto);
        return dto;
    }

    private static void populateDerivedFields(AdminSectionSummaryDto dto) {
        if (dto == null) {
            return;
        }
        Integer dayValue = dto.getDayOfWeek();
        if (dayValue != null && dayValue >= 1 && dayValue <= 7) {
            DayOfWeek day = DayOfWeek.of(dayValue);
            dto.setDayLabel(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        LocalTime start = dto.getStartTime();
        LocalTime end = dto.getEndTime();
        if (start != null) {
            StringBuilder label = new StringBuilder(TIME_FORMATTER.format(start));
            if (end != null) {
                label.append(" - ").append(TIME_FORMATTER.format(end));
            }
            dto.setTimeRangeLabel(label.toString());
        }
        Integer enrolled = dto.getEnrolledCount();
        Integer max = dto.getMaxStudents();
        if (enrolled != null && max != null && max > 0) {
            dto.setEnrollmentSummary(enrolled + "/" + max + " seats");
        } else if (enrolled != null) {
            dto.setEnrollmentSummary(enrolled + " students");
        }
    }

    private static Integer getInteger(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static int toIsoDayOfWeek(int databaseDay) {
        int normalized = Math.floorMod(databaseDay, 7);
        return normalized == 0 ? 7 : normalized;
    }

    private static String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
