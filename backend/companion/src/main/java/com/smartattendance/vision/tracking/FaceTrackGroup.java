package com.smartattendance.vision.tracking;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Comparator;
import java.util.List;

/**
 * Composite managing multiple {@link FaceTrack} instances. Provides a simple
 * update mechanism that matches detections to existing tracks based on
 * bounding box intersection.
 */
public class FaceTrackGroup {
    private final List<FaceTrack> tracks = new ArrayList<>();
    private final long maxAgeMs;

    public FaceTrackGroup(long maxAgeMs) {
        this.maxAgeMs = maxAgeMs;
    }

    /**
     * Updates the tracked faces with the latest detection rectangles.
     * New detections spawn new tracks, and stale tracks are removed.
     */
    public void update(List<Rectangle> detections) {
        for (FaceTrack t : tracks) t.setUpdatedThisFrame(false);
        // Greedy match by IoU
        for (Rectangle r : detections) {
            FaceTrack match = findBestMatchByIoU(r, 0.3);
            if (match != null) {
                match.update(r);
            } else {
                FaceTrack nt = new FaceTrack(r);
                nt.setUpdatedThisFrame(true);
                tracks.add(nt);
            }
        }
        // Hide overlays for tracks that were not refreshed this frame so ghost boxes disappear.
        for (FaceTrack track : tracks) {
            if (!track.wasUpdatedThisFrame()) {
                track.clearDisplayBounds();
            }
        }
        // Remove stale tracks
        Iterator<FaceTrack> it = tracks.iterator();
        while (it.hasNext()) {
            if (it.next().isStale(maxAgeMs)) {
                it.remove();
            }
        }
    }

    /** Returns the current list of active tracks. */
    public List<FaceTrack> getTracks() {
        return tracks;
    }

    private FaceTrack findBestMatchByIoU(Rectangle r, double threshold) {
        FaceTrack best = null;
        double bestIou = threshold;
        for (FaceTrack t : tracks) {
            Rectangle tb = t.getMatchBounds();
            double iou = iou(tb, r);
            if (iou > bestIou) { bestIou = iou; best = t; }
        }
        return best;
    }

    private static double iou(Rectangle a, Rectangle b) {
        if (a == null || b == null) {
            return 0.0;
        }
        int x1 = Math.max(a.x, b.x);
        int y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);
        int iw = Math.max(0, x2 - x1);
        int ih = Math.max(0, y2 - y1);
        double inter = (double) iw * ih;
        double union = (double) a.width * a.height + (double) b.width * b.height - inter;
        if (union <= 0.0) return 0.0;
        return inter / union;
    }
}
