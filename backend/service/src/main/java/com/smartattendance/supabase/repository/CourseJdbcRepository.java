package com.smartattendance.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.smartattendance.supabase.dto.CourseSummaryDto;
import com.smartattendance.supabase.dto.CreateCourseRequest;

@Repository
public class CourseJdbcRepository {

    private static final RowMapper<CourseSummaryDto> COURSE_SUMMARY_MAPPER = CourseJdbcRepository::mapCourseSummary;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CourseJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CourseSummaryDto> findActiveCourses() {
        String sql = """
                SELECT id, course_code, course_title, description, is_active
                  FROM courses
                 WHERE is_active = true
                 ORDER BY course_code
                """;
        return jdbcTemplate.query(sql, COURSE_SUMMARY_MAPPER);
    }

    public List<CourseSummaryDto> findCoursesForProfessor(UUID professorId) {
        String sql = """
                SELECT DISTINCT c.id, c.course_code, c.course_title, c.description, c.is_active
                  FROM sections s
                  JOIN courses c ON c.id = s.course_id
                 WHERE s.professor_id = :prof
                 ORDER BY c.course_code
                """;
        return jdbcTemplate.query(sql, Map.of("prof", professorId), COURSE_SUMMARY_MAPPER);
    }

    public CourseSummaryDto findCourseSummary(UUID courseId) {
        String sql = "SELECT id, course_code, course_title, description, is_active FROM courses WHERE id = :id";
        return jdbcTemplate.queryForObject(sql, Map.of("id", courseId), COURSE_SUMMARY_MAPPER);
    }

    public CourseSummaryDto createCourse(UUID id, CreateCourseRequest request) {
        String sql = """
                INSERT INTO courses (id, course_code, course_title, description, is_active)
                VALUES (:id, :code, :title, :description, true)
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("code", request.getCourseCode())
                .addValue("title", request.getCourseTitle())
                .addValue("description", request.getDescription()));
        return findCourseSummary(id);
    }

    public CourseSummaryDto updateCourse(UUID courseId, CreateCourseRequest request) {
        String sql = """
                UPDATE courses
                   SET course_code = :code,
                       course_title = :title,
                       description = :description
                 WHERE id = :id
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", courseId)
                .addValue("code", request.getCourseCode())
                .addValue("title", request.getCourseTitle())
                .addValue("description", request.getDescription()));
        return findCourseSummary(courseId);
    }

    public void deleteCourse(UUID courseId) {
        if (courseId == null) {
            return;
        }

        MapSqlParameterSource params = new MapSqlParameterSource("courseId", courseId);

        jdbcTemplate.update("""
                DELETE FROM attendance_records
                 WHERE session_id IN (
                       SELECT id FROM attendance_sessions WHERE section_id IN (
                             SELECT id FROM sections WHERE course_id = :courseId))
                """, params);
        jdbcTemplate.update("""
                DELETE FROM attendance_sessions
                 WHERE section_id IN (
                       SELECT id FROM sections WHERE course_id = :courseId)
                """, params);
        jdbcTemplate.update("""
                DELETE FROM zip_tasks
                 WHERE section_id IN (
                       SELECT id FROM sections WHERE course_id = :courseId)
                """, params);
        jdbcTemplate.update("""
                DELETE FROM student_enrollments
                 WHERE section_id IN (
                       SELECT id FROM sections WHERE course_id = :courseId)
                """, params);
        jdbcTemplate.update("DELETE FROM sections WHERE course_id = :courseId", params);
        jdbcTemplate.update("DELETE FROM courses WHERE id = :courseId", params);
    }

    private static CourseSummaryDto mapCourseSummary(ResultSet rs, int rowNum) throws SQLException {
        CourseSummaryDto dto = new CourseSummaryDto();
        dto.setId((UUID) rs.getObject("id"));
        dto.setCourseCode(rs.getString("course_code"));
        dto.setCourseTitle(rs.getString("course_title"));
        String description = null;
        try {
            description = rs.getString("description");
        } catch (SQLException ignored) {
        }
        if (description == null) {
            try {
                description = rs.getString("course_description");
            } catch (SQLException ignored) {
            }
        }
        dto.setDescription(description);
        dto.setActive(rs.getBoolean("is_active"));
        return dto;
    }
}
