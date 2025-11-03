package com.smartattendance.companion.recognition;

/**
 * Lifecycle states for tracked faces rendered on the recognition overlay.
 */
public enum FaceTrackState {
    DETECTED,
    RECOGNIZING,
    AUTO_ACCEPTED,
    MANUAL_REVIEW,
    MANUAL_ACCEPTED,
    MANUAL_REJECTED,
    IGNORED,
    COMPLETED
}
