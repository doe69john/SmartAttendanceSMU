package com.smartattendance.supabase.service.session;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.smartattendance.supabase.dto.SectionModelMetadataDto;
import com.smartattendance.supabase.dto.SessionDetailsDto;
import com.smartattendance.supabase.entity.AttendanceSessionEntity;
import com.smartattendance.supabase.service.recognition.SectionModelService.SectionRetrainResult;

@Component
public class SessionMapper {

    public SessionDetailsDto toDetailsDto(AttendanceSessionEntity entity) {
        return toDetailsDto(entity, null);
    }

    public SessionDetailsDto toDetailsDto(AttendanceSessionEntity entity, SectionRetrainResult retrainResult) {
        if (entity == null) {
            return null;
        }
        SessionDetailsDto dto = new SessionDetailsDto();
        dto.setId(entity.getId());
        dto.setSectionId(entity.getSectionId());
        dto.setProfessorId(entity.getProfessorId());
        dto.setSessionDate(entity.getSessionDate());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setLateThresholdMinutes(entity.getLateThresholdMinutes());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name().toLowerCase(Locale.ROOT) : null);
        dto.setLocation(entity.getLocation());
        dto.setNotes(entity.getNotes());
        if (retrainResult != null) {
            SectionModelMetadataDto metadata = new SectionModelMetadataDto();
            metadata.setStoragePrefix(retrainResult.storagePrefix());
            metadata.setImageCount(retrainResult.imageCount());
            metadata.setMissingStudentIds(retrainResult.missingStudentIds());
            metadata.setModelDownloadPath(retrainResult.modelDownloadPath());
            metadata.setLabelsDownloadPath(retrainResult.labelsDownloadPath());
            metadata.setCascadeDownloadPath(retrainResult.cascadeDownloadPath());
            metadata.setLabelDisplayNames(retrainResult.labelDisplayNames());
            dto.setModelMetadata(metadata);
        }
        return dto;
    }
}

