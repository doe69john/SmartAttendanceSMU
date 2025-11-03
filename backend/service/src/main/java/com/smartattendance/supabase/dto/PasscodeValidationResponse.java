package com.smartattendance.supabase.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PasscodeValidationResponse", description = "Result of validating a staff passcode")
public class PasscodeValidationResponse {

    @Schema(description = "True when the submitted passcode is valid")
    private boolean valid;

    public PasscodeValidationResponse() {
    }

    public PasscodeValidationResponse(boolean valid) {
        this.valid = valid;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }
}
