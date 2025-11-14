package com.smartattendance.supabase.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RosterImportIssue", description = "Details about a row that could not be matched during roster import")
public class RosterImportIssueDto {

    @Schema(description = "1-indexed row number in the uploaded file where the issue occurred")
    private Integer rowNumber;

    @Schema(description = "Raw value(s) that were evaluated for this row")
    private String value;

    @Schema(description = "Human readable description explaining why the row could not be imported")
    private String reason;

    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
