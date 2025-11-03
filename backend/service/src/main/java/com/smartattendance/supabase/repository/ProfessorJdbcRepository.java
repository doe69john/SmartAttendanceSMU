package com.smartattendance.supabase.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.smartattendance.supabase.dto.ProfessorDirectoryEntry;

@Repository
public class ProfessorJdbcRepository {

    private static final RowMapper<ProfessorDirectoryEntry> PROFESSOR_DIRECTORY_MAPPER = ProfessorJdbcRepository::mapDirectoryEntry;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProfessorJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProfessorDirectoryEntry> listProfessors(Boolean active) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,
                       full_name,
                       staff_id,
                       email,
                       is_active
                  FROM profiles
                 WHERE role = 'professor'
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (active != null) {
            sql.append("   AND is_active = :active\n");
            params.addValue("active", active);
        }
        sql.append("                 ORDER BY full_name\n");
        return jdbcTemplate.query(sql.toString(), params, PROFESSOR_DIRECTORY_MAPPER);
    }

    public boolean professorExists(UUID professorId) {
        if (professorId == null) {
            return false;
        }
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM profiles WHERE id = :id AND role = 'professor')
                """, new MapSqlParameterSource("id", professorId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private static ProfessorDirectoryEntry mapDirectoryEntry(ResultSet rs, int rowNum) throws SQLException {
        ProfessorDirectoryEntry entry = new ProfessorDirectoryEntry();
        entry.setId((UUID) rs.getObject("id"));
        entry.setFullName(rs.getString("full_name"));
        entry.setStaffId(rs.getString("staff_id"));
        entry.setEmail(rs.getString("email"));
        Boolean active = (Boolean) rs.getObject("is_active");
        entry.setActive(active);
        return entry;
    }
}
