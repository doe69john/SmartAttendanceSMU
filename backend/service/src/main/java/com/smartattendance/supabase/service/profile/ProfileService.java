package com.smartattendance.supabase.service.profile;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartattendance.supabase.dto.ProfileDto;
import com.smartattendance.supabase.entity.ProfileEntity;
import com.smartattendance.supabase.repository.ProfileRepository;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileDto> findByUserId(UUID userId) {
        return profileRepository.findByUserId(userId).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<ProfileDto> findById(UUID id) {
        return profileRepository.findById(id).map(this::toDto);
    }

    private ProfileDto toDto(ProfileEntity entity) {
        ProfileDto dto = new ProfileDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setEmail(entity.getEmail());
        dto.setFullName(entity.getFullName());
        dto.setRole(entity.getRole() != null ? entity.getRole().name() : null);
        dto.setStaffId(entity.getStaffId());
        dto.setStudentId(entity.getStudentIdentifier());
        dto.setAvatarUrl(entity.getAvatarUrl());
        return dto;
    }
}
