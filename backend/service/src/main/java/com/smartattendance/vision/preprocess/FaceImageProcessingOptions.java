package com.smartattendance.vision.preprocess;

/**
 * Metadata describing how a captured face frame should be cropped prior to preprocessing.
 */
public class FaceImageProcessingOptions {
    private final Integer frameWidth;
    private final Integer frameHeight;
    private final Integer boxX;
    private final Integer boxY;
    private final Integer boxWidth;
    private final Integer boxHeight;

    public FaceImageProcessingOptions(Integer frameWidth,
                                      Integer frameHeight,
                                      Integer boxX,
                                      Integer boxY,
                                      Integer boxWidth,
                                      Integer boxHeight) {
        this.frameWidth = sanitize(frameWidth);
        this.frameHeight = sanitize(frameHeight);
        this.boxX = sanitize(boxX);
        this.boxY = sanitize(boxY);
        this.boxWidth = sanitize(boxWidth);
        this.boxHeight = sanitize(boxHeight);
    }

    public static FaceImageProcessingOptions empty() {
        return new FaceImageProcessingOptions(null, null, null, null, null, null);
    }

    public static FaceImageProcessingOptions of(Integer frameWidth,
                                                Integer frameHeight,
                                                Integer boxX,
                                                Integer boxY,
                                                Integer boxWidth,
                                                Integer boxHeight) {
        return new FaceImageProcessingOptions(frameWidth, frameHeight, boxX, boxY, boxWidth, boxHeight);
    }

    private Integer sanitize(Integer value) {
        if (value == null) {
            return null;
        }
        return value >= 0 ? value : null;
    }

    public Integer getFrameWidth() {
        return frameWidth;
    }

    public Integer getFrameHeight() {
        return frameHeight;
    }

    public Integer getBoxX() {
        return boxX;
    }

    public Integer getBoxY() {
        return boxY;
    }

    public Integer getBoxWidth() {
        return boxWidth;
    }

    public Integer getBoxHeight() {
        return boxHeight;
    }

    public boolean hasFrameDimensions() {
        return frameWidth != null && frameWidth > 0 && frameHeight != null && frameHeight > 0;
    }

    public boolean hasBoundingBox() {
        return hasFrameDimensions()
                && boxX != null && boxY != null
                && boxWidth != null && boxHeight != null
                && boxWidth > 0 && boxHeight > 0;
    }
}
