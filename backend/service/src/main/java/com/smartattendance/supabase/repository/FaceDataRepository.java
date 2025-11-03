package com.smartattendance.supabase.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartattendance.supabase.entity.FaceDataEntity;

public interface FaceDataRepository extends JpaRepository<FaceDataEntity, UUID> {

    List<FaceDataEntity> findByStudentId(UUID studentId);

    List<FaceDataEntity> findByStudentIdIn(Collection<UUID> studentIds);

}
