package com.smartattendance.supabase.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartattendance.supabase.entity.CourseEntity;

public interface CourseRepository extends JpaRepository<CourseEntity, UUID> {

    List<CourseEntity> findByActiveTrue();
}
