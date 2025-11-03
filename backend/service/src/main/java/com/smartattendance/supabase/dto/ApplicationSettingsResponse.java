package com.smartattendance.supabase.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing high-level application configuration flags that are
 * consumed by the SPA during boot.
 */
@Schema(name = "ApplicationSettingsResponse", description = "Indicates whether sensitive operations require shared passcodes")
public class ApplicationSettingsResponse {

    @Schema(description = "True when professor onboarding actions must be guarded by a shared passcode")
    private boolean requiresStaffPasscode;

    @Schema(description = "True when admin onboarding actions must be guarded by a shared passcode")
    private boolean requiresAdminPasscode;

    public ApplicationSettingsResponse() {
    }

    public ApplicationSettingsResponse(boolean requiresStaffPasscode, boolean requiresAdminPasscode) {
        this.requiresStaffPasscode = requiresStaffPasscode;
        this.requiresAdminPasscode = requiresAdminPasscode;
    }

    public boolean isRequiresStaffPasscode() {
        return requiresStaffPasscode;
    }

    public void setRequiresStaffPasscode(boolean requiresStaffPasscode) {
        this.requiresStaffPasscode = requiresStaffPasscode;
    }

    public boolean isRequiresAdminPasscode() {
        return requiresAdminPasscode;
    }

    public void setRequiresAdminPasscode(boolean requiresAdminPasscode) {
        this.requiresAdminPasscode = requiresAdminPasscode;
    }
}
