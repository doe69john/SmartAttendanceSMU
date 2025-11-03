package com.smartattendance.supabase.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartattendance.supabase.entity.SectionEntity;

public interface SectionRepository extends JpaRepository<SectionEntity, UUID> {

    List<SectionEntity> findByProfessorId(UUID professorId);
}
