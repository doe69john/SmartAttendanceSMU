package com.smartattendance.supabase.web.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.smartattendance.supabase.dto.CreateSectionRequest;
import com.smartattendance.supabase.dto.SectionSummaryDto;
import com.smartattendance.supabase.dto.admin.AdminSectionSummaryDto;
import com.smartattendance.supabase.service.admin.AdminSectionService;
import com.smartattendance.supabase.service.reporting.TeachingManagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/sections")
@Tag(name = "Admin Section Management", description = "Administrative views and updates for individual sections")
public class AdminSectionController {

    private final AdminSectionService adminSectionService;
    private final TeachingManagementService teachingManagementService;

    public AdminSectionController(AdminSectionService adminSectionService,
                                  TeachingManagementService teachingManagementService) {
        this.adminSectionService = adminSectionService;
        this.teachingManagementService = teachingManagementService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List sections", description = "Returns all sections with course, professor, and enrollment metadata.")
    public List<AdminSectionSummaryDto> listSections(@RequestParam(name = "q", required = false) String query) {
        return adminSectionService.listSections(query);
    }

    @GetMapping("/{sectionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get section", description = "Loads a single section summary including professor assignment.")
    public AdminSectionSummaryDto getSection(@PathVariable("sectionId") UUID sectionId) {
        return adminSectionService.findSection(sectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));
    }

    @PutMapping("/{sectionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update section", description = "Updates section schedule, capacity, and professor assignment.")
    public SectionSummaryDto updateSection(@PathVariable("sectionId") UUID sectionId,
                                           @RequestBody CreateSectionRequest request) {
        return teachingManagementService.updateSectionWithProfessor(sectionId, request);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create section", description = "Creates a new section with a specified professor assignment.")
    public SectionSummaryDto createSection(@RequestBody CreateSectionRequest request) {
        return teachingManagementService.createSectionWithProfessor(request);
    }
}
