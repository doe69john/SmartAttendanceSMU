package com.smartattendance.companion.recognition;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

/** Provides camera frames to the recognition runtime. */
public interface FrameSource extends AutoCloseable {

    /** Returns the next available frame from the source, or {@code null} if unavailable. */
    BufferedImage getImage();

    /** Preferred display size for this frame source. */
    Dimension getPreferredSize();

    @Override
    void close();
}
