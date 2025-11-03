package com.smartattendance.supabase.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "PasscodeValidationRequest", description = "Payload containing a staff passcode candidate")
public class PasscodeValidationRequest {

    @Schema(description = "Candidate passcode to validate", maxLength = 128)
    @Size(max = 128)
    private String passcode;

    public String getPasscode() {
        return passcode;
    }

    public void setPasscode(String passcode) {
        this.passcode = passcode;
    }
}
