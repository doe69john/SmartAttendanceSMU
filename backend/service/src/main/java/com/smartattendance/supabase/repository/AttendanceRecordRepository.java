package com.smartattendance.supabase.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.smartattendance.supabase.entity.AttendanceRecordEntity;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecordEntity, UUID>,
        JpaSpecificationExecutor<AttendanceRecordEntity> {

    Optional<AttendanceRecordEntity> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    List<AttendanceRecordEntity> findBySessionId(UUID sessionId);

    List<AttendanceRecordEntity> findBySessionIdOrderByMarkedAtDesc(UUID sessionId);
}
