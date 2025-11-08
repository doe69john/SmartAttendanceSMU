package com.smartattendance.vision.tracking;

import java.awt.Rectangle;
import java.util.UUID;

/**
 * Represents a single tracked face across frames. Maintains the
 * last seen time and bounding rectangle of the face.
 */
public class FaceTrack {
    private final String id = UUID.randomUUID().toString();
    private Rectangle rawBounds;
    private Rectangle displayBounds;
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
        if (bounds != null) {
            this.rawBounds = new Rectangle(bounds);
            this.displayBounds = new Rectangle(bounds);
        }
        touch();
    }

    /** Updates this track with a new bounding rectangle. */
    public void update(Rectangle newBounds) {
        if (newBounds == null) {
            return;
        }

        Rectangle normalized = new Rectangle(newBounds);
        Rectangle previousRaw = this.rawBounds;

        // accumulate simple motion based on center delta (use raw new bounds)
        if (previousRaw != null) {
            int cx0 = previousRaw.x + previousRaw.width / 2;
            int cy0 = previousRaw.y + previousRaw.height / 2;
            int cx1 = normalized.x + normalized.width / 2;
            int cy1 = normalized.y + normalized.height / 2;
            double dx = cx1 - cx0;
            double dy = cy1 - cy0;
            motionAccum += Math.hypot(dx, dy);
        }

        this.prevBounds = this.displayBounds;
        this.rawBounds = normalized;

        // Apply mild exponential smoothing to reduce jitter in UI
        if (this.displayBounds != null) {
            double a = 0.35; // smoothing factor for stability vs. responsiveness
            int sx = (int)Math.round(a * normalized.x + (1 - a) * this.displayBounds.x);
            int sy = (int)Math.round(a * normalized.y + (1 - a) * this.displayBounds.y);
            int sw = (int)Math.round(a * normalized.width + (1 - a) * this.displayBounds.width);
            int sh = (int)Math.round(a * normalized.height + (1 - a) * this.displayBounds.height);
            this.displayBounds = new Rectangle(sx, sy, Math.max(1, sw), Math.max(1, sh));
        } else {
            this.displayBounds = normalized;
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

    public Rectangle getBounds() { return getDisplayBounds(); }

    /** Returns the smoothed rectangle suitable for UI overlays. */
    public Rectangle getDisplayBounds() { return displayBounds; }

    /** Returns the raw rectangle used for matching & gating logic. */
    public Rectangle getMatchBounds() { return rawBounds; }

    public int getSeenFrames() { return seenFrames; }
    public double getMotionAccum() { return motionAccum; }
    public Rectangle getPrevBounds() { return prevBounds; }
    public long getLastSeen() { return lastSeen; }
    public boolean wasUpdatedThisFrame() { return updatedThisFrame; }
    public void setUpdatedThisFrame(boolean v) { this.updatedThisFrame = v; }

    /** Clears the smoothed display bounds so the overlay can be hidden. */
    public void clearDisplayBounds() { this.displayBounds = null; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public long getLastRecognizedAt() { return lastRecognizedAt; }
    public void markRecognizedNow() { this.lastRecognizedAt = System.currentTimeMillis(); }
    public double getLastConfidence() { return lastConfidence; }
    public void setLastConfidence(double c) { this.lastConfidence = c; }
}
