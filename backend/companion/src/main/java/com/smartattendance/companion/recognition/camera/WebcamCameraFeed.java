package com.smartattendance.companion.recognition.camera;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;

import com.github.sarxos.webcam.Webcam;

/** Camera feed backed by the legacy webcam-capture driver (used primarily on Windows). */
public final class WebcamCameraFeed implements CameraFeed {

    private final Webcam webcam;
    private final Dimension preferredSize;

    private WebcamCameraFeed(Webcam webcam, Dimension preferredSize) {
        this.webcam = webcam;
        this.preferredSize = preferredSize;
    }

    public static WebcamCameraFeed open(int index) {
        List<Webcam> webcams = Webcam.getWebcams();
        if (webcams.isEmpty()) {
            throw new IllegalStateException("No webcams detected on this device");
        }
        int resolvedIndex = Math.max(0, Math.min(index, webcams.size() - 1));
        Webcam cam = webcams.get(resolvedIndex);
        Dimension preferred = new Dimension(960, 540);
        cam.setCustomViewSizes(new Dimension(1280, 720), preferred);
        cam.setViewSize(preferred);
        if (!cam.isOpen()) {
            cam.open(true);
        }
        return new WebcamCameraFeed(cam, preferred);
    }

    @Override
    public BufferedImage captureFrame() {
        return webcam.getImage();
    }

    @Override
    public Dimension preferredSize() {
        return preferredSize;
    }

    @Override
    public void close() {
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
    }
}
