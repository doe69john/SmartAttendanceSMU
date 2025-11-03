package com.smartattendance.supabase.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SessionAttendanceStats", description = "Aggregated attendance counts for a session")
public class SessionAttendanceStatsDto {

    @Schema(description = "Total number of attendance records")
    private int total;

    @Schema(description = "Number of present records")
    private int present;

    @Schema(description = "Number of late records")
    private int late;

    @Schema(description = "Number of absent records")
    private int absent;

    @Schema(description = "Number of pending records")
    private int pending;

    @Schema(description = "Records captured automatically by recognition")
    private int automatic;

    @Schema(description = "Records captured manually by the professor")
    private int manual;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPresent() {
        return present;
    }

    public void setPresent(int present) {
        this.present = present;
    }

    public int getLate() {
        return late;
    }

    public void setLate(int late) {
        this.late = late;
    }

    public int getAbsent() {
        return absent;
    }

    public void setAbsent(int absent) {
        this.absent = absent;
    }

    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    public int getAutomatic() {
        return automatic;
    }

    public void setAutomatic(int automatic) {
        this.automatic = automatic;
    }

    public int getManual() {
        return manual;
    }

    public void setManual(int manual) {
        this.manual = manual;
    }
}
