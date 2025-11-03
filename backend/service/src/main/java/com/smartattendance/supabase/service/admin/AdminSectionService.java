package com.smartattendance.supabase.service.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.smartattendance.supabase.dto.admin.AdminSectionSummaryDto;
import com.smartattendance.supabase.repository.AdminSectionJdbcRepository;

@Service
public class AdminSectionService {

    private final AdminSectionJdbcRepository adminSectionJdbcRepository;

    public AdminSectionService(AdminSectionJdbcRepository adminSectionJdbcRepository) {
        this.adminSectionJdbcRepository = adminSectionJdbcRepository;
    }

    public List<AdminSectionSummaryDto> listSections(String query) {
        return adminSectionJdbcRepository.findSectionSummaries(query);
    }

    public Optional<AdminSectionSummaryDto> findSection(UUID sectionId) {
        if (sectionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(adminSectionJdbcRepository.findSectionSummary(sectionId));
    }
}
