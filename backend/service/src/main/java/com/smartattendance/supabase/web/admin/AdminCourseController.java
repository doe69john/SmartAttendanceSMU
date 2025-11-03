package com.smartattendance.supabase.web.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartattendance.supabase.dto.admin.AdminCourseSectionDto;
import com.smartattendance.supabase.dto.admin.AdminCourseStudentDto;
import com.smartattendance.supabase.dto.admin.AdminCourseSummaryDto;
import com.smartattendance.supabase.service.admin.AdminCourseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/courses")
@Tag(name = "Admin Course Management", description = "Administrative views of courses, sections, and enrollment")
public class AdminCourseController {

    private final AdminCourseService adminCourseService;

    public AdminCourseController(AdminCourseService adminCourseService) {
        this.adminCourseService = adminCourseService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List courses", description = "Returns all courses with aggregated enrollment statistics.")
    public List<AdminCourseSummaryDto> listCourses(@RequestParam(name = "q", required = false) String query) {
        return adminCourseService.listCourses(query);
    }

    @GetMapping("/{courseId}/sections")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Course sections", description = "Returns sections for the course including professor and attendance metrics.")
    public List<AdminCourseSectionDto> listCourseSections(@PathVariable("courseId") UUID courseId,
                                                          @RequestParam(name = "q", required = false) String query) {
        return adminCourseService.listCourseSections(courseId, query);
    }

    @GetMapping("/{courseId}/students")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Course students", description = "Returns enrolled students for the course with attendance summaries.")
    public List<AdminCourseStudentDto> listCourseStudents(@PathVariable("courseId") UUID courseId,
                                                          @RequestParam(name = "q", required = false) String query) {
        return adminCourseService.listCourseStudents(courseId, query);
    }
}
