package com.smartattendance.companion.recognition;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;

import com.github.sarxos.webcam.Webcam;

/** {@link FrameSource} backed by the webcam-capture library. */
public final class WebcamFrameSource implements FrameSource {

    private static final Dimension PREFERRED_SIZE = new Dimension(960, 540);

    private final Webcam webcam;

    public WebcamFrameSource(int index) {
        List<Webcam> webcams = Webcam.getWebcams();
        if (webcams.isEmpty()) {
            throw new IllegalStateException("No webcams detected on this device");
        }
        int safeIndex = Math.max(0, Math.min(index, webcams.size() - 1));
        webcam = webcams.get(safeIndex);
        webcam.setCustomViewSizes(new Dimension(1280, 720), PREFERRED_SIZE);
        webcam.setViewSize(PREFERRED_SIZE);
        if (!webcam.isOpen()) {
            webcam.open(true);
        }
    }

    @Override
    public BufferedImage getImage() {
        return webcam.getImage();
    }

    @Override
    public Dimension getPreferredSize() {
        return PREFERRED_SIZE;
    }

    @Override
    public void close() {
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
    }

}
