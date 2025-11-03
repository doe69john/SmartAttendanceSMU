package com.smartattendance.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.smartattendance.supabase.dto.admin.AdminCourseSectionDto;
import com.smartattendance.supabase.dto.admin.AdminCourseStudentDto;
import com.smartattendance.supabase.dto.admin.AdminCourseSummaryDto;

@Repository
public class AdminCourseJdbcRepository {

    private static final RowMapper<AdminCourseSummaryDto> COURSE_SUMMARY_MAPPER = AdminCourseJdbcRepository::mapCourseSummary;
    private static final RowMapper<AdminCourseSectionDto> COURSE_SECTION_MAPPER = AdminCourseJdbcRepository::mapCourseSection;
    private static final RowMapper<AdminCourseStudentDto> COURSE_STUDENT_MAPPER = AdminCourseJdbcRepository::mapCourseStudent;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminCourseJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminCourseSummaryDto> findCourseSummaries(String query) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id,
                       c.course_code,
                       c.course_title,
                       c.description,
                       COALESCE(c.is_active, true) AS is_active,
                       COUNT(DISTINCT s.id) AS section_count,
                       COUNT(DISTINCT CASE WHEN s.professor_id IS NOT NULL THEN s.professor_id END) AS professor_count,
                       COUNT(DISTINCT CASE WHEN se.is_active = true THEN se.student_id END) AS student_count
                  FROM courses c
                  LEFT JOIN sections s ON s.course_id = c.id
                  LEFT JOIN student_enrollments se ON se.section_id = s.id AND se.is_active = true
                """);

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (query != null && !query.isBlank()) {
            String normalized = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append("                 WHERE LOWER(c.course_code) LIKE :search\n");
            sql.append("                    OR LOWER(c.course_title) LIKE :search\n");
            sql.append("                    OR LOWER(COALESCE(c.description, '')) LIKE :search\n");
            params.addValue("search", normalized);
        }

        sql.append("                 GROUP BY c.id, c.course_code, c.course_title, c.description, c.is_active\n");
        sql.append("                 ORDER BY LOWER(c.course_code) ASC\n");

        return jdbcTemplate.query(sql.toString(), params, COURSE_SUMMARY_MAPPER);
    }

    public List<AdminCourseSectionDto> findCourseSections(UUID courseId, String query) {
        StringBuilder sql = new StringBuilder("""
                SELECT sec.id,
                       sec.section_code,
                       sec.day_of_week,
                       TO_CHAR(sec.start_time, 'HH24:MI') AS start_time,
                       TO_CHAR(sec.end_time, 'HH24:MI') AS end_time,
                       sec.location,
                       COALESCE(sec.is_active, true) AS is_active,
                       prof.id AS professor_id,
                       prof.full_name AS professor_name,
                       prof.email AS professor_email,
                       COUNT(DISTINCT CASE WHEN se.is_active = true THEN se.student_id END) AS student_count,
                       COUNT(DISTINCT sess.id) AS session_count,
                       CASE WHEN COUNT(ar.id) FILTER (WHERE ar.status IS NOT NULL) = 0 THEN NULL
                            ELSE SUM(CASE WHEN ar.status IN ('present','late') THEN 1 ELSE 0 END)::double precision
                                 / COUNT(ar.id) FILTER (WHERE ar.status IS NOT NULL)
                       END AS attendance_rate
                  FROM sections sec
                  LEFT JOIN profiles prof ON prof.id = sec.professor_id
                  LEFT JOIN student_enrollments se ON se.section_id = sec.id AND se.is_active = true
                  LEFT JOIN attendance_sessions sess ON sess.section_id = sec.id
                  LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = se.student_id
                 WHERE sec.course_id = :courseId
                """);

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("courseId", courseId);
        if (query != null && !query.isBlank()) {
            String normalized = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append("                   AND (LOWER(sec.section_code) LIKE :search\n");
            sql.append("                        OR LOWER(COALESCE(prof.full_name, '')) LIKE :search)\n");
            params.addValue("search", normalized);
        }

        sql.append("                 GROUP BY sec.id, sec.section_code, sec.day_of_week, sec.start_time, sec.end_time, sec.location, sec.is_active,\n");
        sql.append("                          prof.id, prof.full_name, prof.email\n");
        sql.append("                 ORDER BY LOWER(sec.section_code) ASC\n");

        return jdbcTemplate.query(sql.toString(), params, COURSE_SECTION_MAPPER);
    }

    public List<AdminCourseStudentDto> findCourseStudents(UUID courseId, String query) {
        StringBuilder sql = new StringBuilder("""
                SELECT stud.id AS student_id,
                       stud.full_name,
                       stud.email,
                       stud.student_id AS student_number,
                       sec.id AS section_id,
                       sec.section_code,
                       COUNT(DISTINCT sess.id) AS total_sessions,
                       COUNT(DISTINCT CASE WHEN ar.status IS NOT NULL THEN sess.id END) AS recorded_sessions,
                       COUNT(DISTINCT CASE WHEN ar.status IN ('present','late') THEN sess.id END) AS attended_sessions,
                       CASE WHEN COUNT(ar.id) FILTER (WHERE ar.status IS NOT NULL) = 0 THEN NULL
                            ELSE SUM(CASE WHEN ar.status IN ('present','late') THEN 1 ELSE 0 END)::double precision
                                 / COUNT(ar.id) FILTER (WHERE ar.status IS NOT NULL)
                       END AS attendance_rate
                  FROM student_enrollments se
                  JOIN profiles stud ON stud.id = se.student_id
                  JOIN sections sec ON sec.id = se.section_id
                  LEFT JOIN attendance_sessions sess ON sess.section_id = sec.id
                  LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = stud.id
                 WHERE sec.course_id = :courseId
                   AND se.is_active = true
                """);

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("courseId", courseId);
        if (query != null && !query.isBlank()) {
            String normalized = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append("                   AND (LOWER(stud.full_name) LIKE :search\n");
            sql.append("                        OR LOWER(COALESCE(stud.email, '')) LIKE :search\n");
            sql.append("                        OR LOWER(COALESCE(stud.student_id, '')) LIKE :search)\n");
            params.addValue("search", normalized);
        }

        sql.append("                 GROUP BY stud.id, stud.full_name, stud.email, stud.student_id, sec.id, sec.section_code\n");
        sql.append("                 ORDER BY LOWER(stud.full_name) ASC, LOWER(sec.section_code) ASC\n");

        return jdbcTemplate.query(sql.toString(), params, COURSE_STUDENT_MAPPER);
    }

    private static AdminCourseSummaryDto mapCourseSummary(ResultSet rs, int rowNum) throws SQLException {
        AdminCourseSummaryDto dto = new AdminCourseSummaryDto();
        dto.setCourseId((UUID) rs.getObject("id"));
        dto.setCourseCode(rs.getString("course_code"));
        dto.setCourseTitle(rs.getString("course_title"));
        dto.setDescription(rs.getString("description"));
        dto.setActive(rs.getBoolean("is_active"));
        dto.setSectionCount(rs.getInt("section_count"));
        dto.setProfessorCount(rs.getInt("professor_count"));
        dto.setStudentCount(rs.getInt("student_count"));
        return dto;
    }

    private static AdminCourseSectionDto mapCourseSection(ResultSet rs, int rowNum) throws SQLException {
        AdminCourseSectionDto dto = new AdminCourseSectionDto();
        dto.setSectionId((UUID) rs.getObject("id"));
        dto.setSectionCode(rs.getString("section_code"));
        Integer rawDayOfWeek = (Integer) rs.getObject("day_of_week");
        dto.setDayOfWeek(rawDayOfWeek != null ? toIsoDayOfWeek(rawDayOfWeek) : null);
        dto.setStartTime(rs.getString("start_time"));
        dto.setEndTime(rs.getString("end_time"));
        dto.setLocation(rs.getString("location"));
        dto.setActive(rs.getBoolean("is_active"));
        dto.setProfessorId((UUID) rs.getObject("professor_id"));
        dto.setProfessorName(rs.getString("professor_name"));
        dto.setProfessorEmail(rs.getString("professor_email"));
        dto.setStudentCount(rs.getInt("student_count"));
        dto.setSessionCount(rs.getInt("session_count"));
        double attendanceRate = rs.getDouble("attendance_rate");
        dto.setAttendanceRate(rs.wasNull() ? null : attendanceRate);
        return dto;
    }

    private static AdminCourseStudentDto mapCourseStudent(ResultSet rs, int rowNum) throws SQLException {
        AdminCourseStudentDto dto = new AdminCourseStudentDto();
        dto.setStudentId((UUID) rs.getObject("student_id"));
        dto.setFullName(rs.getString("full_name"));
        dto.setEmail(rs.getString("email"));
        dto.setStudentNumber(rs.getString("student_number"));
        dto.setSectionId((UUID) rs.getObject("section_id"));
        dto.setSectionCode(rs.getString("section_code"));
        dto.setTotalSessions(rs.getInt("total_sessions"));
        dto.setRecordedSessions(rs.getInt("recorded_sessions"));
        dto.setAttendedSessions(rs.getInt("attended_sessions"));
        double attendanceRate = rs.getDouble("attendance_rate");
        dto.setAttendanceRate(rs.wasNull() ? null : attendanceRate);
        return dto;
    }

    private static int toIsoDayOfWeek(int databaseDay) {
        int normalized = Math.floorMod(databaseDay, 7);
        return normalized == 0 ? 7 : normalized;
    }
}
