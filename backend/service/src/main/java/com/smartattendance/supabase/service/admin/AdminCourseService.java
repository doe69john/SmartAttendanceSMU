package com.smartattendance.supabase.service.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.smartattendance.supabase.dto.admin.AdminCourseSectionDto;
import com.smartattendance.supabase.dto.admin.AdminCourseStudentDto;
import com.smartattendance.supabase.dto.admin.AdminCourseSummaryDto;
import com.smartattendance.supabase.repository.AdminCourseJdbcRepository;

@Service
public class AdminCourseService {

    private final AdminCourseJdbcRepository adminCourseJdbcRepository;

    public AdminCourseService(AdminCourseJdbcRepository adminCourseJdbcRepository) {
        this.adminCourseJdbcRepository = adminCourseJdbcRepository;
    }

    public List<AdminCourseSummaryDto> listCourses(String query) {
        return adminCourseJdbcRepository.findCourseSummaries(query);
    }

    public List<AdminCourseSectionDto> listCourseSections(UUID courseId, String query) {
        return adminCourseJdbcRepository.findCourseSections(courseId, query);
    }

    public List<AdminCourseStudentDto> listCourseStudents(UUID courseId, String query) {
        return adminCourseJdbcRepository.findCourseStudents(courseId, query);
    }
}
