package com.smartattendance.vision.tracking;

import java.awt.Rectangle;
import java.util.UUID;

/**
 * Represents a single tracked face across frames. Maintains the
 * last seen time and bounding rectangle of the face.
 */
public class FaceTrack {
    private final String id = UUID.randomUUID().toString();
    private Rectangle bounds;
    private long lastSeen;
    private boolean updatedThisFrame = false;
    // Stabilization & recognition helpers
    private int seenFrames = 0;
    private double motionAccum = 0.0; // sum of center deltas (pixels)
    private Rectangle prevBounds = null;
    private String label = null;
    private long lastRecognizedAt = 0L;
    private double lastConfidence = 0.0;

    public FaceTrack(Rectangle bounds) {
        this.bounds = bounds;
        touch();
    }

    /** Updates this track with a new bounding rectangle. */
    public void update(Rectangle newBounds) {
        // accumulate simple motion based on center delta (use raw new bounds)
        if (this.bounds != null) {
            int cx0 = this.bounds.x + this.bounds.width / 2;
            int cy0 = this.bounds.y + this.bounds.height / 2;
            int cx1 = newBounds.x + newBounds.width / 2;
            int cy1 = newBounds.y + newBounds.height / 2;
            double dx = cx1 - cx0;
            double dy = cy1 - cy0;
            motionAccum += Math.hypot(dx, dy);
        }
        this.prevBounds = this.bounds;

        // Apply mild exponential smoothing to reduce jitter in UI
        if (this.bounds != null) {
            double a = 0.35; // smoothing factor for stability vs. responsiveness
            int sx = (int)Math.round(a * newBounds.x + (1 - a) * this.bounds.x);
            int sy = (int)Math.round(a * newBounds.y + (1 - a) * this.bounds.y);
            int sw = (int)Math.round(a * newBounds.width + (1 - a) * this.bounds.width);
            int sh = (int)Math.round(a * newBounds.height + (1 - a) * this.bounds.height);
            this.bounds = new Rectangle(sx, sy, Math.max(1, sw), Math.max(1, sh));
        } else {
            this.bounds = newBounds;
        }

        seenFrames++;
        touch();
        this.updatedThisFrame = true;
    }

    private void touch() {
        this.lastSeen = System.currentTimeMillis();
    }

    /** Returns true if this track has not been updated within the given age. */
    public boolean isStale(long maxAgeMs) {
        return System.currentTimeMillis() - lastSeen > maxAgeMs;
    }

    public String getId() {
        return id;
    }

    public Rectangle getBounds() {
        return bounds;
    }

    public int getSeenFrames() { return seenFrames; }
    public double getMotionAccum() { return motionAccum; }
    public Rectangle getPrevBounds() { return prevBounds; }
    public long getLastSeen() { return lastSeen; }
    public boolean wasUpdatedThisFrame() { return updatedThisFrame; }
    public void setUpdatedThisFrame(boolean v) { this.updatedThisFrame = v; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public long getLastRecognizedAt() { return lastRecognizedAt; }
    public void markRecognizedNow() { this.lastRecognizedAt = System.currentTimeMillis(); }
    public double getLastConfidence() { return lastConfidence; }
    public void setLastConfidence(double c) { this.lastConfidence = c; }
}
