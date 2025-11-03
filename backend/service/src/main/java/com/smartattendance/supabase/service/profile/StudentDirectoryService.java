package com.smartattendance.supabase.service.profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.smartattendance.supabase.dto.StudentDto;
import com.smartattendance.supabase.entity.ProfileEntity;
import com.smartattendance.supabase.repository.ProfileRepository;

/**
 * Helper service that centralises retrieval and mapping of student profile data
 * into {@link StudentDto} instances. Several higher-level services require the
 * same mapping logic; consolidating it here keeps those services lean while
 * ensuring consistent transformations.
 */
@Service
public class StudentDirectoryService {

    private final ProfileRepository profileRepository;

    public StudentDirectoryService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public Optional<StudentDto> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return profileRepository.findById(id).map(this::toStudentDto);
    }

    public Map<UUID, StudentDto> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<UUID> uniqueIds = ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        var idList = List.copyOf(uniqueIds);
        return profileRepository.findByIdIn(idList).stream()
                .filter(Objects::nonNull)
                .map(this::toStudentDto)
                .collect(Collectors.toMap(StudentDto::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    public List<StudentDto> listByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> orderedIds = ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, StudentDto> mapped = findByIds(List.copyOf(orderedIds));
        List<StudentDto> results = new ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            StudentDto dto = mapped.get(id);
            if (dto != null) {
                results.add(dto);
            }
        }
        if (results.size() != mapped.size()) {
            mapped.values().stream()
                    .filter(dto -> !orderedIds.contains(dto.getId()))
                    .forEach(results::add);
        }
        return results;
    }

    public List<StudentDto> searchStudents(String query, int limit) {
        int pageSize = Math.max(1, Math.min(limit, 50));
        Pageable pageable = PageRequest.of(0, pageSize);
        String sanitized = query != null ? query.trim() : "";

        List<ProfileEntity> matches;
        if (sanitized.isEmpty()) {
            matches = profileRepository.findActiveStudents(pageable);
        } else {
            String lowered = sanitized.toLowerCase(Locale.ROOT);
            matches = profileRepository.searchActiveStudents(lowered, pageable);
        }

        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        return matches.stream()
                .filter(Objects::nonNull)
                .map(this::toStudentDto)
                .toList();
    }

    private StudentDto toStudentDto(ProfileEntity profile) {
        StudentDto dto = new StudentDto();
        dto.setId(profile.getId());
        dto.setFullName(profile.getFullName());
        dto.setStudentNumber(profile.getStudentIdentifier());
        dto.setAvatarUrl(profile.getAvatarUrl());
        dto.setEmail(profile.getEmail());
        return dto;
    }
}

