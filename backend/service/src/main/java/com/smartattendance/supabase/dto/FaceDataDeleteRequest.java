package com.smartattendance.supabase.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FaceDataDeleteRequest", description = "Payload for deleting face data records")
public class FaceDataDeleteRequest {

    @JsonProperty("ids")
    @Schema(description = "Identifiers of face data records to delete")
    private List<UUID> ids;

    public List<UUID> getIds() {
        return ids;
    }

    public void setIds(List<UUID> ids) {
        this.ids = ids;
    }
}
