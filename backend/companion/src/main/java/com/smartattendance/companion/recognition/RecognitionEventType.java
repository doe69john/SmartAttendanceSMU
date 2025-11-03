package com.smartattendance.companion.recognition;

/**
 * High-level categories for recognition lifecycle events.
 */
public enum RecognitionEventType {
    CAMERA_STARTED,
    CAMERA_STOPPED,
    FRAME_PROCESSED,
    FACE_DETECTED,
    TRACK_LOST,
    AUTO_ACCEPTED,
    AUTO_REJECTED,
    MANUAL_CONFIRMATION_REQUIRED,
    MANUAL_CONFIRMED,
    MANUAL_REJECTED,
    ATTENDANCE_RECORDED,
    ATTENDANCE_SKIPPED,
    ERROR
}
