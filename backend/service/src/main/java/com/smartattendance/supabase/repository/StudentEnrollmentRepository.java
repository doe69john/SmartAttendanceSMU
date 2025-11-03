package com.smartattendance.supabase.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartattendance.supabase.entity.StudentEnrollmentEntity;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollmentEntity, UUID> {

    List<StudentEnrollmentEntity> findBySectionIdAndActiveTrue(UUID sectionId);

    java.util.Optional<StudentEnrollmentEntity> findBySectionIdAndStudentId(UUID sectionId, UUID studentId);

    List<StudentEnrollmentEntity> findByStudentIdAndActiveTrue(UUID studentId);
}
