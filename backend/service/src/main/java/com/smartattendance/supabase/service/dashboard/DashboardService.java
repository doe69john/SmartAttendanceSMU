package com.smartattendance.supabase.service.dashboard;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.smartattendance.supabase.dto.ProfessorDashboardSummary;
import com.smartattendance.supabase.dto.StudentDashboardSummary;

@Service
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DashboardService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProfessorDashboardSummary loadProfessorSummary(UUID professorId) {
        if (professorId == null) {
            return new ProfessorDashboardSummary();
        }
        Map<String, Object> counts = jdbcTemplate.queryForMap("""
                SELECT
                    (SELECT COUNT(*) FROM sections s WHERE s.professor_id = :professor AND s.is_active = true) AS total_sections,
                    (SELECT COUNT(*) FROM student_enrollments se JOIN sections s ON s.id = se.section_id
                       WHERE s.professor_id = :professor AND se.is_active = true) AS total_students,
                    (SELECT COUNT(*) FROM attendance_sessions sess
                       WHERE sess.professor_id = :professor AND sess.status = 'scheduled' AND sess.session_date >= :today) AS upcoming_sessions,
                    (SELECT COUNT(*) FROM attendance_sessions sess
                       WHERE sess.professor_id = :professor AND sess.status = 'active') AS active_sessions
                """,
                Map.of(
                        "professor", professorId,
                        "today", LocalDate.now()));
        ProfessorDashboardSummary summary = new ProfessorDashboardSummary();
        summary.setTotalSections(((Number) counts.getOrDefault("total_sections", 0)).intValue());
        summary.setTotalStudents(((Number) counts.getOrDefault("total_students", 0)).intValue());
        summary.setUpcomingSessions(((Number) counts.getOrDefault("upcoming_sessions", 0)).intValue());
        summary.setActiveSessions(((Number) counts.getOrDefault("active_sessions", 0)).intValue());
        return summary;
    }

    public StudentDashboardSummary loadStudentSummary(UUID studentId) {
        if (studentId == null) {
            return new StudentDashboardSummary();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("student", studentId)
                .addValue("today", LocalDate.now());
        Map<String, Object> counts = jdbcTemplate.queryForMap("""
                SELECT
                    (SELECT COUNT(*) FROM student_enrollments se WHERE se.student_id = :student AND se.is_active = true) AS enrolled_sections,
                    (SELECT COUNT(*) FROM attendance_sessions sess
                       JOIN student_enrollments se ON se.section_id = sess.section_id
                      WHERE se.student_id = :student
                        AND se.is_active = true
                        AND sess.status = 'scheduled'
                        AND sess.session_date >= :today) AS upcoming_sessions,
                    (SELECT COUNT(*) FROM attendance_records ar
                       WHERE ar.student_id = :student AND ar.status = 'present') AS attended_sessions,
                    (SELECT COUNT(*) FROM attendance_records ar
                       WHERE ar.student_id = :student) AS total_records,
                    (SELECT COUNT(*) FROM attendance_records ar
                       WHERE ar.student_id = :student AND ar.status = 'absent') AS missed_sessions
                """, params);
        StudentDashboardSummary summary = new StudentDashboardSummary();
        int attended = ((Number) counts.getOrDefault("attended_sessions", 0)).intValue();
        int total = ((Number) counts.getOrDefault("total_records", 0)).intValue();
        summary.setEnrolledSections(((Number) counts.getOrDefault("enrolled_sections", 0)).intValue());
        summary.setUpcomingSessions(((Number) counts.getOrDefault("upcoming_sessions", 0)).intValue());
        summary.setAttendedSessions(attended);
        summary.setMissedSessions(((Number) counts.getOrDefault("missed_sessions", 0)).intValue());
        summary.setAttendanceRate(total > 0 ? (double) attended / total : 0.0);
        return summary;
    }
}
