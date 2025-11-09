package com.smartattendance.companion.recognition;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.smartattendance.util.OpenCVUtils;

/** {@link FrameSource} backed by OpenCV's {@link VideoCapture}. */
public final class OpenCvFrameSource implements FrameSource {

    private static final Logger log = LoggerFactory.getLogger(OpenCvFrameSource.class);
    private static final Dimension PREFERRED_SIZE = new Dimension(960, 540);

    private final VideoCapture capture;

    public OpenCvFrameSource(int index, double fps) {
        capture = new VideoCapture(index);
        if (!capture.isOpened()) {
            throw new IllegalStateException("Unable to open default camera (index=" + index + ")");
        }
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, PREFERRED_SIZE.width);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, PREFERRED_SIZE.height);
        if (Double.isFinite(fps) && fps > 0.0d) {
            capture.set(Videoio.CAP_PROP_FPS, fps);
        }
    }

    @Override
    public BufferedImage getImage() {
        Mat frame = new Mat();
        boolean success = capture.read(frame);
        if (!success || frame.empty()) {
            frame.release();
            return null;
        }
        try {
            return OpenCVUtils.matToBufferedImage(frame);
        } catch (Exception ex) {
            log.warn("Failed to convert camera frame: {}", ex.getMessage(), ex);
            return null;
        } finally {
            frame.release();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return PREFERRED_SIZE;
    }

    @Override
    public void close() {
        if (capture != null) {
            try {
                capture.release();
            } catch (Exception ignored) {
            }
        }
    }
}
