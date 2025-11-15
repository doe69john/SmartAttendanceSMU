package com.smartattendance.companion.recognition.camera;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

import com.smartattendance.util.OpenCVUtils;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

/** Camera feed backed directly by OpenCV's VideoCapture (used for Apple Silicon macOS). */
public final class OpenCvCameraFeed implements CameraFeed {

    private final VideoCapture capture;
    private final Dimension preferredSize;
    private final Mat frame = new Mat();
    private final AtomicBoolean released = new AtomicBoolean(false);

    private OpenCvCameraFeed(VideoCapture capture, Dimension preferredSize) {
        this.capture = capture;
        this.preferredSize = preferredSize;
    }

    public static OpenCvCameraFeed open(int index, Dimension preferredSize, double fps) {
        VideoCapture videoCapture = new VideoCapture(index);
        if (!videoCapture.isOpened()) {
            videoCapture.release();
            throw new IllegalStateException("Unable to open camera index " + index);
        }
        Dimension target = preferredSize != null ? preferredSize : new Dimension(960, 540);
        videoCapture.set(Videoio.CAP_PROP_FRAME_WIDTH, target.width);
        videoCapture.set(Videoio.CAP_PROP_FRAME_HEIGHT, target.height);
        if (Double.isFinite(fps) && fps > 0) {
            videoCapture.set(Videoio.CAP_PROP_FPS, fps);
        }
        return new OpenCvCameraFeed(videoCapture, target);
    }

    @Override
    public synchronized BufferedImage captureFrame() {
        if (released.get()) {
            return null;
        }
        if (!capture.read(frame) || frame.empty()) {
            return null;
        }
        return OpenCVUtils.matToBufferedImage(frame);
    }

    @Override
    public Dimension preferredSize() {
        return preferredSize;
    }

    @Override
    public void close() {
        if (released.compareAndSet(false, true)) {
            capture.release();
            frame.release();
        }
    }
}
