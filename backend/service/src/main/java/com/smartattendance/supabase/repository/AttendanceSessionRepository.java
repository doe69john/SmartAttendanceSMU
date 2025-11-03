package com.smartattendance.supabase.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartattendance.supabase.entity.AttendanceSessionEntity;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSessionEntity, UUID> {
}
