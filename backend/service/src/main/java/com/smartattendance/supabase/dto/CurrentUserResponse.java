package com.smartattendance.supabase.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CurrentUserResponse", description = "Response containing the authenticated user's profile and roles")
public class CurrentUserResponse {

    @Schema(description = "Profile details for the authenticated user")
    private ProfileDto profile;

    @Schema(description = "Authorities granted to the authenticated user")
    private List<String> roles;

    public ProfileDto getProfile() {
        return profile;
    }

    public void setProfile(ProfileDto profile) {
        this.profile = profile;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
