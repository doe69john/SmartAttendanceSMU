package com.smartattendance.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import com.smartattendance.supabase.dto.admin.AdminUserDetailDto;
import com.smartattendance.supabase.dto.admin.AdminUserSummaryDto;
import com.smartattendance.supabase.dto.admin.AdminUserUpdateRequest;

@Repository
public class AdminUserJdbcRepository {

    private static final RowMapper<AdminUserSummaryDto> USER_MAPPER = AdminUserJdbcRepository::mapUser;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminUserJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminUserSummaryDto> listUsers(String role, String query, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("""
                SELECT p.id,
                       p.user_id,
                       p.full_name,
                       p.email,
                       p.phone,
                       p.role,
                       p.staff_id,
                       p.student_id,
                       p.is_active,
                       p.created_at,
                       p.updated_at,
                       (SELECT COUNT(*) FROM face_data fd WHERE fd.student_id = p.id) AS face_data_count
                  FROM profiles p
                 WHERE 1 = 1
                """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (role != null && !role.isBlank()) {
            sql.append(" AND p.role = CAST(:role AS user_role)");
            params.addValue("role", role.toLowerCase(Locale.ROOT));
        }

        if (query != null && !query.isBlank()) {
            sql.append("""
                     AND (
                         LOWER(p.full_name) LIKE :pattern
                      OR LOWER(p.email) LIKE :pattern
                      OR LOWER(COALESCE(p.staff_id, '')) LIKE :pattern
                      OR LOWER(COALESCE(p.student_id, '')) LIKE :pattern
                     )
                    """);
            params.addValue("pattern", '%' + query.toLowerCase(Locale.ROOT) + '%');
        }

        sql.append(" ORDER BY LOWER(p.full_name) NULLS LAST");
        sql.append(" LIMIT :limit");
        params.addValue("limit", Math.max(1, limit));

        return jdbcTemplate.query(sql.toString(), params, USER_MAPPER);
    }

    public Optional<AdminUserDetailDto> findUser(UUID profileId) {
        if (profileId == null) {
            return Optional.empty();
        }
        Map<String, Object> params = Map.of("id", profileId);
        List<AdminUserDetailDto> results = jdbcTemplate.query("""
                SELECT p.id,
                       p.user_id,
                       p.full_name,
                       p.email,
                       p.phone,
                       p.role,
                       p.staff_id,
                       p.student_id,
                       p.is_active,
                       p.created_at,
                       p.updated_at,
                       (SELECT COUNT(*) FROM face_data fd WHERE fd.student_id = p.id) AS face_data_count
                  FROM profiles p
                 WHERE p.id = :id
                """, params, AdminUserJdbcRepository::mapUserDetail);
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.getFirst());
    }

    public void updateProfile(UUID profileId, AdminUserUpdateRequest request) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", profileId)
                .addValue("full_name", request.getFullName())
                .addValue("email", request.getEmail())
                .addValue("phone", emptyToNull(request.getPhone()))
                .addValue("staff_id", emptyToNull(request.getStaffId()))
                .addValue("student_id", emptyToNull(request.getStudentId()))
                .addValue("is_active", request.getActive());

        jdbcTemplate.update("""
                UPDATE profiles
                   SET full_name = :full_name,
                       email = :email,
                       phone = :phone,
                       staff_id = :staff_id,
                       student_id = :student_id,
                       is_active = COALESCE(:is_active, is_active),
                       updated_at = NOW()
                 WHERE id = :id
                """, params);
    }

    public void updateAuthUserEmail(UUID userId, String email) {
        if (userId == null || email == null || email.isBlank()) {
            return;
        }
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("user_id", userId)
                .addValue("email", email);
        jdbcTemplate.update("UPDATE auth.users SET email = :email WHERE id = :user_id", params);
    }

    public void deleteUser(UUID profileId, UUID userId) throws DataAccessException {
        if (profileId == null) {
            return;
        }
        Map<String, Object> params = Map.of("profile_id", profileId);
        jdbcTemplate.update("DELETE FROM face_data WHERE student_id = :profile_id", params);
        jdbcTemplate.update("DELETE FROM attendance_records WHERE student_id = :profile_id", params);
        jdbcTemplate.update("DELETE FROM student_enrollments WHERE student_id = :profile_id", params);
        jdbcTemplate.update("UPDATE sections SET professor_id = NULL WHERE professor_id = :profile_id", params);
        jdbcTemplate.update("DELETE FROM profiles WHERE id = :profile_id", params);
        if (userId != null) {
            Map<String, Object> authParams = Map.of("user_id", userId);
            jdbcTemplate.update("DELETE FROM auth.users WHERE id = :user_id", authParams);
        }
    }

    public boolean professorHasSectionAssignments(UUID profileId) {
        if (profileId == null) {
            return false;
        }
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM sections WHERE professor_id = :profile_id)
                """, Map.of("profile_id", profileId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public boolean professorHasSessions(UUID profileId) {
        if (profileId == null) {
            return false;
        }
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM attendance_sessions WHERE professor_id = :profile_id)
                """, Map.of("profile_id", profileId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private static AdminUserSummaryDto mapUser(ResultSet rs, int rowNum) throws SQLException {
        AdminUserSummaryDto dto = new AdminUserSummaryDto();
        dto.setId(getUuid(rs, "id"));
        dto.setUserId(getUuid(rs, "user_id"));
        dto.setFullName(rs.getString("full_name"));
        dto.setEmail(rs.getString("email"));
        dto.setPhone(rs.getString("phone"));
        dto.setRole(rs.getString("role"));
        dto.setStaffId(rs.getString("staff_id"));
        dto.setStudentId(rs.getString("student_id"));
        dto.setActive((Boolean) rs.getObject("is_active"));
        dto.setCreatedAt(getOffsetDateTime(rs, "created_at"));
        dto.setUpdatedAt(getOffsetDateTime(rs, "updated_at"));
        Integer faceDataCount = getInteger(rs, "face_data_count");
        dto.setFaceDataCount(faceDataCount);
        dto.setHasFaceData(faceDataCount != null && faceDataCount > 0);
        return dto;
    }

    private static AdminUserDetailDto mapUserDetail(ResultSet rs, int rowNum) throws SQLException {
        AdminUserDetailDto dto = new AdminUserDetailDto();
        dto.setId(getUuid(rs, "id"));
        dto.setUserId(getUuid(rs, "user_id"));
        dto.setFullName(rs.getString("full_name"));
        dto.setEmail(rs.getString("email"));
        dto.setPhone(rs.getString("phone"));
        dto.setRole(rs.getString("role"));
        dto.setStaffId(rs.getString("staff_id"));
        dto.setStudentId(rs.getString("student_id"));
        dto.setActive((Boolean) rs.getObject("is_active"));
        dto.setCreatedAt(getOffsetDateTime(rs, "created_at"));
        dto.setUpdatedAt(getOffsetDateTime(rs, "updated_at"));
        Integer faceDataCount = getInteger(rs, "face_data_count");
        dto.setFaceDataCount(faceDataCount);
        dto.setHasFaceData(faceDataCount != null && faceDataCount > 0);
        return dto;
    }

    private static UUID getUuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text && !text.isBlank()) {
            return UUID.fromString(text);
        }
        return null;
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant().atOffset(ZoneOffset.UTC) : null;
    }

    private static Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
