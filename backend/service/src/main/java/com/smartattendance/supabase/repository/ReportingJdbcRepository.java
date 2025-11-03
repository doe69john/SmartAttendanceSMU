package com.smartattendance.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.smartattendance.supabase.dto.ProfessorStudentReportDto;
import com.smartattendance.supabase.dto.StudentAttendanceHistoryDto;
import com.smartattendance.supabase.dto.StudentDto;
import com.smartattendance.supabase.dto.StudentSectionReportDto;

@Repository
public class ReportingJdbcRepository {

    private static final RowMapper<ProfessorStudentReportDto> STUDENT_REPORT_MAPPER = ReportingJdbcRepository::mapStudentReport;
    private static final RowMapper<StudentSectionReportDto> STUDENT_SECTION_MAPPER = ReportingJdbcRepository::mapStudentSection;
    private static final RowMapper<StudentAttendanceHistoryDto> ATTENDANCE_HISTORY_MAPPER = ReportingJdbcRepository::mapAttendanceHistory;
    private static final RowMapper<SectionAttendanceExportRow> SECTION_EXPORT_MAPPER = ReportingJdbcRepository::mapSectionExportRow;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReportingJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProfessorStudentReportDto> findProfessorStudents(UUID professorId, String query) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("prof", professorId);
        String filter = "";
        if (query != null && !query.isBlank()) {
            String normalized = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            filter = "   AND (LOWER(p.full_name) LIKE :query\n"
                    + "        OR LOWER(p.student_id) LIKE :query\n"
                    + "        OR LOWER(p.email) LIKE :query)\n";
            params.addValue("query", normalized);
        }
        String sql = String.format(BASE_STUDENT_QUERY, filter) + " ORDER BY p.full_name ASC\n";
        return jdbcTemplate.query(sql, params, STUDENT_REPORT_MAPPER);
    }

    public List<ProfessorStudentReportDto> findAdminStudents(String query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String filter = "";
        if (query != null && !query.isBlank()) {
            String normalized = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            filter = "   AND (LOWER(p.full_name) LIKE :query\n"
                    + "        OR LOWER(p.student_id) LIKE :query\n"
                    + "        OR LOWER(p.email) LIKE :query)\n";
            params.addValue("query", normalized);
        }
        String sql = String.format(ADMIN_STUDENT_QUERY, filter) + " ORDER BY p.full_name ASC\n";
        return jdbcTemplate.query(sql, params, STUDENT_REPORT_MAPPER);
    }

    public Optional<ProfessorStudentReportDto> findProfessorStudent(UUID professorId, UUID studentId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("prof", professorId)
                .addValue("student", studentId);
        String sql = String.format(BASE_STUDENT_QUERY, "   AND se.student_id = :student\n") + " ORDER BY p.full_name ASC";
        List<ProfessorStudentReportDto> rows = jdbcTemplate.query(sql, params, STUDENT_REPORT_MAPPER);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    public List<StudentSectionReportDto> findStudentSections(UUID professorId, UUID studentId) {
        String sql = """
                SELECT se.student_id,
                       sec.id AS section_id,
                       sec.section_code,
                       sec.course_id,
                       c.course_code,
                       c.course_title,
                       COUNT(DISTINCT sess.id) AS total_sessions,
                       COUNT(DISTINCT CASE WHEN ar.status IN ('present','late') THEN sess.id END) AS attended_sessions,
                       COUNT(DISTINCT CASE WHEN ar.status = 'absent' THEN sess.id END) AS missed_sessions,
                       COUNT(DISTINCT CASE WHEN ar.status IS NOT NULL THEN sess.id END) AS recorded_sessions
                  FROM sections sec
                  JOIN courses c ON c.id = sec.course_id
                  JOIN student_enrollments se ON se.section_id = sec.id AND se.is_active = true
                  LEFT JOIN attendance_sessions sess ON sess.section_id = sec.id
                  LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = se.student_id
                 WHERE sec.professor_id = :prof
                   AND se.student_id = :student
                   AND (sec.is_active = true OR sec.is_active IS NULL)
                 GROUP BY se.student_id, sec.id, c.course_code, c.course_title, sec.section_code, sec.course_id
                 ORDER BY c.course_code, sec.section_code
                """;
        return jdbcTemplate.query(sql,
                Map.of("prof", professorId, "student", studentId),
                STUDENT_SECTION_MAPPER);
    }

    public List<StudentAttendanceHistoryDto> findStudentAttendanceHistory(UUID professorId, UUID studentId) {
        String sql = """
                SELECT sess.id AS session_id,
                       sess.section_id,
                       sec.section_code,
                       c.course_code,
                       c.course_title,
                       sess.session_date,
                       sess.start_time,
                       sess.end_time,
                       ar.status,
                       ar.marked_at,
                       ar.marking_method,
                       ar.notes,
                       sess.location
                  FROM sections sec
                  JOIN courses c ON c.id = sec.course_id
                  JOIN student_enrollments se ON se.section_id = sec.id AND se.student_id = :student AND se.is_active = true
                  JOIN attendance_sessions sess ON sess.section_id = sec.id
                  LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = :student
                 WHERE sec.professor_id = :prof
                   AND (sec.is_active = true OR sec.is_active IS NULL)
                 ORDER BY sess.session_date DESC, sess.start_time DESC NULLS LAST
                """;
        return jdbcTemplate.query(sql,
                Map.of("prof", professorId, "student", studentId),
                ATTENDANCE_HISTORY_MAPPER);
    }

    public Optional<ProfessorStudentReportDto> findStudentSelfSummary(UUID studentId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("student", studentId);
        String sql = String.format(STUDENT_SELF_BASE_QUERY, "");
        List<ProfessorStudentReportDto> rows = jdbcTemplate.query(sql, params, STUDENT_REPORT_MAPPER);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    public List<StudentSectionReportDto> findStudentSelfSections(UUID studentId, UUID sectionId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("student", studentId);
        StringBuilder sql = new StringBuilder("""
                SELECT se.student_id,
                       sec.id AS section_id,
                       sec.section_code,
                       sec.course_id,
                       c.course_code,
                       c.course_title,
                       COUNT(DISTINCT sess.id) AS total_sessions,
                       COUNT(DISTINCT CASE WHEN ar.status IN ('present','late') THEN sess.id END) AS attended_sessions,
                       COUNT(DISTINCT CASE WHEN ar.status = 'absent' THEN sess.id END) AS missed_sessions,
                       COUNT(DISTINCT CASE WHEN ar.status IS NOT NULL THEN sess.id END) AS recorded_sessions
                  FROM sections sec
                  JOIN courses c ON c.id = sec.course_id
                  JOIN student_enrollments se ON se.section_id = sec.id AND se.student_id = :student AND se.is_active = true
                  LEFT JOIN attendance_sessions sess ON sess.section_id = sec.id
                  LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = se.student_id
                 WHERE sec.is_active = true OR sec.is_active IS NULL
                """);
        if (sectionId != null) {
            sql.append("   AND sec.id = :section\n");
            params.addValue("section", sectionId);
        }
        sql.append("                 GROUP BY se.student_id, sec.id, c.course_code, c.course_title, sec.section_code, sec.course_id\n"
                + "                 ORDER BY c.course_code, sec.section_code\n");
        return jdbcTemplate.query(sql.toString(),
                params,
                STUDENT_SECTION_MAPPER);
    }

    public List<StudentAttendanceHistoryDto> findStudentSelfAttendanceHistory(UUID studentId, UUID sectionId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("student", studentId);
        StringBuilder sql = new StringBuilder("""
                SELECT sess.id AS session_id,
                       sess.section_id,
                       sec.section_code,
                       c.course_code,
                       c.course_title,
                       sess.session_date,
                       sess.start_time,
                       sess.end_time,
                       ar.status,
                       ar.marked_at,
                       ar.marking_method,
                       ar.notes,
                       sess.location
                  FROM sections sec
                  JOIN courses c ON c.id = sec.course_id
                  JOIN student_enrollments se ON se.section_id = sec.id AND se.student_id = :student AND se.is_active = true
                  JOIN attendance_sessions sess ON sess.section_id = sec.id
                  LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = :student
                 WHERE sec.is_active = true OR sec.is_active IS NULL
                """);
        if (sectionId != null) {
            sql.append("   AND sec.id = :section\n");
            sql.append("   AND sess.section_id = :section\n");
            sql.append("   AND se.section_id = :section\n");
            params.addValue("section", sectionId);
        }
        sql.append("                 ORDER BY sess.session_date DESC, sess.start_time DESC NULLS LAST\n");
        return jdbcTemplate.query(sql.toString(), params, ATTENDANCE_HISTORY_MAPPER);
    }

    public List<SectionAttendanceExportRow> findSectionAttendance(UUID professorId, UUID sectionId) {
        String sql = """
                SELECT sec.id AS section_id,
                       sess.id AS session_id,
                       sess.session_date,
                       sess.start_time,
                       sess.end_time,
                       sess.location,
                       ar.status,
                       ar.marked_at,
                       ar.marking_method,
                       ar.notes,
                       p.id AS student_id,
                       p.full_name,
                       p.student_id AS student_number,
                       p.email,
                       c.course_code,
                       c.course_title,
                       sec.section_code
                  FROM sections sec
                  JOIN courses c ON c.id = sec.course_id
                  JOIN attendance_sessions sess ON sess.section_id = sec.id
                  JOIN student_enrollments se ON se.section_id = sec.id AND se.is_active = true
                  JOIN profiles p ON p.id = se.student_id
                  LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = se.student_id
                 WHERE sec.professor_id = :prof
                   AND sec.id = :section
                   AND (sec.is_active = true OR sec.is_active IS NULL)
                 ORDER BY p.full_name ASC, sess.session_date ASC, sess.start_time ASC NULLS LAST
                """;
        return jdbcTemplate.query(sql,
                Map.of("prof", professorId, "section", sectionId),
                SECTION_EXPORT_MAPPER);
    }

    public List<SectionAttendanceExportRow> findSectionAttendanceForAdmin(UUID sectionId) {
        String sql = """
                SELECT sec.id AS section_id,
                       sess.id AS session_id,
                       sess.session_date,
                       sess.start_time,
                       sess.end_time,
                       sess.location,
                       ar.status,
                       ar.marked_at,
                       ar.marking_method,
                       ar.notes,
                       p.id AS student_id,
                       p.full_name,
                       p.student_id AS student_number,
                       p.email,
                       c.course_code,
                       c.course_title,
                       sec.section_code
                  FROM sections sec
                  JOIN courses c ON c.id = sec.course_id
                  JOIN attendance_sessions sess ON sess.section_id = sec.id
                  JOIN student_enrollments se ON se.section_id = sec.id AND se.is_active = true
                  JOIN profiles p ON p.id = se.student_id
                  LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = se.student_id
                 WHERE sec.id = :section
                   AND (sec.is_active = true OR sec.is_active IS NULL)
                 ORDER BY p.full_name ASC, sess.session_date ASC, sess.start_time ASC NULLS LAST
                """;
        return jdbcTemplate.query(sql,
                Map.of("section", sectionId),
                SECTION_EXPORT_MAPPER);
    }

    public Optional<SectionAttendanceExportRow> findSectionMetadata(UUID professorId, UUID sectionId) {
        String sql = """
                SELECT sec.id AS section_id,
                       sec.section_code,
                       sec.course_id,
                       c.course_code,
                       c.course_title,
                       sec.location,
                       COUNT(DISTINCT CASE WHEN se.is_active THEN se.student_id END) AS enrolled_students,
                       COALESCE(sec.max_students, 0) AS max_students
                  FROM sections sec
                  JOIN courses c ON c.id = sec.course_id
                  LEFT JOIN student_enrollments se ON se.section_id = sec.id
                 WHERE sec.professor_id = :prof
                   AND sec.id = :section
                   AND (sec.is_active = true OR sec.is_active IS NULL)
                 GROUP BY sec.id, sec.section_code, sec.course_id, c.course_code, c.course_title
                """;
        List<SectionAttendanceExportRow> rows = jdbcTemplate.query(sql,
                Map.of("prof", professorId, "section", sectionId),
                ReportingJdbcRepository::mapSectionMetadata);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    public Optional<SectionAttendanceExportRow> findSectionMetadataForAdmin(UUID sectionId) {
        String sql = """
                SELECT sec.id AS section_id,
                       sec.section_code,
                       sec.course_id,
                       c.course_code,
                       c.course_title,
                       sec.location,
                       COUNT(DISTINCT CASE WHEN se.is_active THEN se.student_id END) AS enrolled_students,
                       COALESCE(sec.max_students, 0) AS max_students
                  FROM sections sec
                  JOIN courses c ON c.id = sec.course_id
                  LEFT JOIN student_enrollments se ON se.section_id = sec.id
                 WHERE sec.id = :section
                   AND (sec.is_active = true OR sec.is_active IS NULL)
                 GROUP BY sec.id, sec.section_code, sec.course_id, c.course_code, c.course_title
                """;
        List<SectionAttendanceExportRow> rows = jdbcTemplate.query(sql,
                Map.of("section", sectionId),
                ReportingJdbcRepository::mapSectionMetadata);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    private static ProfessorStudentReportDto mapStudentReport(ResultSet rs, int rowNum) throws SQLException {
        ProfessorStudentReportDto dto = new ProfessorStudentReportDto();
        StudentDto student = new StudentDto();
        student.setId((UUID) rs.getObject("student_id"));
        student.setFullName(rs.getString("full_name"));
        student.setStudentNumber(rs.getString("student_number"));
        student.setEmail(rs.getString("email"));
        student.setAvatarUrl(rs.getString("avatar_url"));
        dto.setStudent(student);
        dto.setSectionCount(rs.getInt("section_count"));
        dto.setCourseCount(rs.getInt("course_count"));
        dto.setTotalSessions(rs.getInt("total_sessions"));
        dto.setAttendedSessions(rs.getInt("attended_sessions"));
        dto.setMissedSessions(rs.getInt("missed_sessions"));
        dto.setRecordedSessions(rs.getInt("recorded_sessions"));
        int recorded = dto.getRecordedSessions();
        int attended = dto.getAttendedSessions();
        dto.setAttendanceRate(recorded > 0 ? (double) attended / recorded : 0.0);
        dto.setLastAttendanceAt((OffsetDateTime) rs.getObject("last_marked_at", OffsetDateTime.class));
        return dto;
    }

    private static StudentSectionReportDto mapStudentSection(ResultSet rs, int rowNum) throws SQLException {
        StudentSectionReportDto dto = new StudentSectionReportDto();
        dto.setSectionId((UUID) rs.getObject("section_id"));
        dto.setCourseId((UUID) rs.getObject("course_id"));
        dto.setCourseCode(rs.getString("course_code"));
        dto.setCourseTitle(rs.getString("course_title"));
        dto.setSectionCode(rs.getString("section_code"));
        dto.setTotalSessions(rs.getInt("total_sessions"));
        dto.setAttendedSessions(rs.getInt("attended_sessions"));
        dto.setMissedSessions(rs.getInt("missed_sessions"));
        dto.setRecordedSessions(rs.getInt("recorded_sessions"));
        int recorded = dto.getRecordedSessions();
        int attended = dto.getAttendedSessions();
        dto.setAttendanceRate(recorded > 0 ? (double) attended / recorded : 0.0);
        return dto;
    }

    private static StudentAttendanceHistoryDto mapAttendanceHistory(ResultSet rs, int rowNum) throws SQLException {
        StudentAttendanceHistoryDto dto = new StudentAttendanceHistoryDto();
        dto.setSessionId((UUID) rs.getObject("session_id"));
        dto.setSectionId((UUID) rs.getObject("section_id"));
        dto.setSectionCode(rs.getString("section_code"));
        dto.setCourseCode(rs.getString("course_code"));
        dto.setCourseTitle(rs.getString("course_title"));
        dto.setSessionDate(rs.getObject("session_date", java.time.LocalDate.class));
        dto.setStartTime(rs.getObject("start_time", OffsetDateTime.class));
        dto.setEndTime(rs.getObject("end_time", OffsetDateTime.class));
        dto.setStatus(rs.getString("status"));
        dto.setMarkedAt(rs.getObject("marked_at", OffsetDateTime.class));
        dto.setMarkingMethod(rs.getString("marking_method"));
        dto.setNotes(rs.getString("notes"));
        dto.setLocation(rs.getString("location"));
        return dto;
    }

    private static SectionAttendanceExportRow mapSectionExportRow(ResultSet rs, int rowNum) throws SQLException {
        SectionAttendanceExportRow row = new SectionAttendanceExportRow();
        row.sectionId = (UUID) rs.getObject("section_id");
        row.sectionCode = rs.getString("section_code");
        row.courseCode = rs.getString("course_code");
        row.courseTitle = rs.getString("course_title");
        row.studentId = (UUID) rs.getObject("student_id");
        row.studentName = rs.getString("full_name");
        row.studentNumber = rs.getString("student_number");
        row.studentEmail = rs.getString("email");
        row.sessionId = (UUID) rs.getObject("session_id");
        row.sessionDate = rs.getObject("session_date", java.time.LocalDate.class);
        row.startTime = rs.getObject("start_time", OffsetDateTime.class);
        row.endTime = rs.getObject("end_time", OffsetDateTime.class);
        row.status = rs.getString("status");
        row.markedAt = rs.getObject("marked_at", OffsetDateTime.class);
        row.markingMethod = rs.getString("marking_method");
        row.notes = rs.getString("notes");
        row.location = rs.getString("location");
        return row;
    }

    private static SectionAttendanceExportRow mapSectionMetadata(ResultSet rs, int rowNum) throws SQLException {
        SectionAttendanceExportRow row = new SectionAttendanceExportRow();
        row.sectionId = (UUID) rs.getObject("section_id");
        row.sectionCode = rs.getString("section_code");
        row.courseCode = rs.getString("course_code");
        row.courseTitle = rs.getString("course_title");
        row.courseId = (UUID) rs.getObject("course_id");
        row.location = rs.getString("location");
        row.enrolledStudents = rs.getInt("enrolled_students");
        row.maxStudents = rs.getInt("max_students");
        return row;
    }

    private static final String BASE_STUDENT_QUERY = """
            SELECT se.student_id,
                   p.full_name,
                   p.student_id AS student_number,
                   p.email,
                   p.avatar_url,
                   COUNT(DISTINCT se.section_id) AS section_count,
                   COUNT(DISTINCT sec.course_id) AS course_count,
                   COUNT(DISTINCT sess.id) AS total_sessions,
                   COUNT(DISTINCT CASE WHEN ar.status IN ('present','late') THEN sess.id END) AS attended_sessions,
                   COUNT(DISTINCT CASE WHEN ar.status = 'absent' THEN sess.id END) AS missed_sessions,
                   COUNT(DISTINCT CASE WHEN ar.status IS NOT NULL THEN sess.id END) AS recorded_sessions,
                   MAX(ar.marked_at) AS last_marked_at
              FROM sections sec
              JOIN student_enrollments se ON se.section_id = sec.id AND se.is_active = true
              JOIN profiles p ON p.id = se.student_id
              LEFT JOIN attendance_sessions sess ON sess.section_id = sec.id
              LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = se.student_id
             WHERE sec.professor_id = :prof
               AND (sec.is_active = true OR sec.is_active IS NULL)
%s
            GROUP BY se.student_id, p.full_name, p.student_id, p.email, p.avatar_url
            """;

    private static final String ADMIN_STUDENT_QUERY = """
            SELECT se.student_id,
                   p.full_name,
                   p.student_id AS student_number,
                   p.email,
                   p.avatar_url,
                   COUNT(DISTINCT se.section_id) AS section_count,
                   COUNT(DISTINCT sec.course_id) AS course_count,
                   COUNT(DISTINCT sess.id) AS total_sessions,
                   COUNT(DISTINCT CASE WHEN ar.status IN ('present','late') THEN sess.id END) AS attended_sessions,
                   COUNT(DISTINCT CASE WHEN ar.status = 'absent' THEN sess.id END) AS missed_sessions,
                   COUNT(DISTINCT CASE WHEN ar.status IS NOT NULL THEN sess.id END) AS recorded_sessions,
                   MAX(ar.marked_at) AS last_marked_at
              FROM sections sec
              JOIN student_enrollments se ON se.section_id = sec.id AND se.is_active = true
              JOIN profiles p ON p.id = se.student_id
              LEFT JOIN attendance_sessions sess ON sess.section_id = sec.id
              LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = se.student_id
             WHERE (sec.is_active = true OR sec.is_active IS NULL)
%s
            GROUP BY se.student_id, p.full_name, p.student_id, p.email, p.avatar_url
            """;

    private static final String STUDENT_SELF_BASE_QUERY = """
            SELECT se.student_id,
                   p.full_name,
                   p.student_id AS student_number,
                   p.email,
                   p.avatar_url,
                   COUNT(DISTINCT se.section_id) AS section_count,
                   COUNT(DISTINCT sec.course_id) AS course_count,
                   COUNT(DISTINCT sess.id) AS total_sessions,
                   COUNT(DISTINCT CASE WHEN ar.status IN ('present','late') THEN sess.id END) AS attended_sessions,
                   COUNT(DISTINCT CASE WHEN ar.status = 'absent' THEN sess.id END) AS missed_sessions,
                   COUNT(DISTINCT CASE WHEN ar.status IS NOT NULL THEN sess.id END) AS recorded_sessions,
                   MAX(ar.marked_at) AS last_marked_at
              FROM sections sec
              JOIN student_enrollments se ON se.section_id = sec.id AND se.is_active = true
              JOIN profiles p ON p.id = se.student_id
              LEFT JOIN attendance_sessions sess ON sess.section_id = sec.id
              LEFT JOIN attendance_records ar ON ar.session_id = sess.id AND ar.student_id = se.student_id
             WHERE se.student_id = :student
               AND (sec.is_active = true OR sec.is_active IS NULL)
%s
            GROUP BY se.student_id, p.full_name, p.student_id, p.email, p.avatar_url
            """;

    public static class SectionAttendanceExportRow {
        UUID courseId;
        UUID sectionId;
        UUID studentId;
        UUID sessionId;
        String sectionCode;
        String courseCode;
        String courseTitle;
        String studentName;
        String studentNumber;
        String studentEmail;
        java.time.LocalDate sessionDate;
        OffsetDateTime startTime;
        OffsetDateTime endTime;
        String status;
        OffsetDateTime markedAt;
        String markingMethod;
        String notes;
        String location;
        Integer enrolledStudents;
        Integer maxStudents;

        public UUID getCourseId() {
            return courseId;
        }

        public UUID getSectionId() {
            return sectionId;
        }

        public UUID getStudentId() {
            return studentId;
        }

        public UUID getSessionId() {
            return sessionId;
        }

        public String getSectionCode() {
            return sectionCode;
        }

        public String getCourseCode() {
            return courseCode;
        }

        public String getCourseTitle() {
            return courseTitle;
        }

        public String getStudentName() {
            return studentName;
        }

        public String getStudentNumber() {
            return studentNumber;
        }

        public String getStudentEmail() {
            return studentEmail;
        }

        public java.time.LocalDate getSessionDate() {
            return sessionDate;
        }

        public OffsetDateTime getStartTime() {
            return startTime;
        }

        public OffsetDateTime getEndTime() {
            return endTime;
        }

        public String getStatus() {
            return status;
        }

        public OffsetDateTime getMarkedAt() {
            return markedAt;
        }

        public String getMarkingMethod() {
            return markingMethod;
        }

        public String getNotes() {
            return notes;
        }

        public String getLocation() {
            return location;
        }

        public Integer getEnrolledStudents() {
            return enrolledStudents;
        }

        public Integer getMaxStudents() {
            return maxStudents;
        }
    }
}
