package com.smartattendance.companion.recognition.camera;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

/** Abstraction for retrieving frames from a physical camera. */
public interface CameraFeed extends AutoCloseable {

    /** Grabs the next available frame from the camera, or {@code null} if unavailable. */
    BufferedImage captureFrame();

    /** Preferred resolution for rendering the feed. */
    Dimension preferredSize();

    @Override
    void close();
}
