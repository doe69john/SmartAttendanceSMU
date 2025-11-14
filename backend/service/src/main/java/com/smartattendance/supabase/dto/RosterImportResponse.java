package com.smartattendance.supabase.dto;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RosterImportResponse", description = "Summary of a batch roster import attempt")
public class RosterImportResponse {

    @Schema(description = "Number of rows processed after skipping headers and blank lines")
    private int processedCount;

    @Schema(description = "Number of unique students matched")
    private int matchedCount;

    @Schema(description = "Number of duplicate entries detected in the file")
    private int duplicateCount;

    @Schema(description = "Students that can be added to the roster")
    private List<StudentDto> students = new ArrayList<>();

    @Schema(description = "Rows that could not be matched along with reasons")
    private List<RosterImportIssueDto> issues = new ArrayList<>();

    public int getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(int processedCount) {
        this.processedCount = processedCount;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(int matchedCount) {
        this.matchedCount = matchedCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(int duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public List<StudentDto> getStudents() {
        return students;
    }

    public void setStudents(List<StudentDto> students) {
        this.students = students != null ? students : new ArrayList<>();
    }

    public List<RosterImportIssueDto> getIssues() {
        return issues;
    }

    public void setIssues(List<RosterImportIssueDto> issues) {
        this.issues = issues != null ? issues : new ArrayList<>();
    }
}
