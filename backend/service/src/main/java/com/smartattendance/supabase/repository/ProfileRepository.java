package com.smartattendance.supabase.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartattendance.supabase.entity.ProfileEntity;

public interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {

    List<ProfileEntity> findByIdIn(List<UUID> ids);

    Optional<ProfileEntity> findByUserId(UUID userId);

    Optional<ProfileEntity> findByEmailIgnoreCase(String email);

    Optional<ProfileEntity> findByStudentIdentifier(String studentIdentifier);

    Optional<ProfileEntity> findByStaffId(String staffId);

    @Query("""
            SELECT p
              FROM ProfileEntity p
             WHERE p.role = com.smartattendance.supabase.entity.ProfileEntity$Role.student
               AND (p.active IS NULL OR p.active = TRUE)
             ORDER BY LOWER(p.fullName)
            """)
    List<ProfileEntity> findActiveStudents(Pageable pageable);

    @Query("""
            SELECT p
              FROM ProfileEntity p
             WHERE p.role = com.smartattendance.supabase.entity.ProfileEntity$Role.student
               AND (p.active IS NULL OR p.active = TRUE)
               AND (
                    LOWER(p.fullName) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(p.email) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(COALESCE(p.studentIdentifier, '')) LIKE LOWER(CONCAT('%', :query, '%'))
               )
             ORDER BY LOWER(p.fullName)
            """)
    List<ProfileEntity> searchActiveStudents(@Param("query") String query, Pageable pageable);
}
