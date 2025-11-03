package com.smartattendance.supabase.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FaceCaptureAnalysisResponse", description = "Result of analysing a capture frame prior to enrollment")
public class FaceCaptureAnalysisResponse {

    @Schema(description = "Whether the frame passes face presence checks (exactly one face detected)")
    private boolean valid;

    @Schema(description = "Number of faces detected in the frame")
    private int faceCount;

    @Schema(description = "Human readable guidance for the caller")
    private String message;

    @Schema(description = "Laplacian variance computed for the frame, higher means sharper")
    private double sharpness;

    @Schema(description = "Average luminance (0-1) estimated from the frame")
    private double brightness;

    @Schema(description = "Detected face bounding boxes in pixel coordinates")
    private List<BoundingBox> boundingBoxes = List.of();

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public int getFaceCount() {
        return faceCount;
    }

    public void setFaceCount(int faceCount) {
        this.faceCount = faceCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public double getSharpness() {
        return sharpness;
    }

    public void setSharpness(double sharpness) {
        this.sharpness = sharpness;
    }

    public double getBrightness() {
        return brightness;
    }

    public void setBrightness(double brightness) {
        this.brightness = brightness;
    }

    public List<BoundingBox> getBoundingBoxes() {
        return boundingBoxes;
    }

    public void setBoundingBoxes(List<BoundingBox> boundingBoxes) {
        this.boundingBoxes = boundingBoxes != null ? List.copyOf(boundingBoxes) : List.of();
    }

    public static final class BoundingBox {
        @Schema(description = "Top-left x coordinate in pixels")
        private int x;
        @Schema(description = "Top-left y coordinate in pixels")
        private int y;
        @Schema(description = "Bounding box width in pixels")
        private int width;
        @Schema(description = "Bounding box height in pixels")
        private int height;

        public BoundingBox() {
        }

        public BoundingBox(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }
}
