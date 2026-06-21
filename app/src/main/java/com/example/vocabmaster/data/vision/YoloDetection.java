package com.example.vocabmaster.data.vision;

public class YoloDetection {
    private final String label;
    private final float confidence;
    private final float left;
    private final float top;
    private final float right;
    private final float bottom;

    public YoloDetection(String label, float confidence, float left, float top, float right, float bottom) {
        this.label = label;
        this.confidence = confidence;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public String getLabel() {
        return label;
    }

    public float getConfidence() {
        return confidence;
    }

    public float getLeft() {
        return left;
    }

    public float getTop() {
        return top;
    }

    public float getRight() {
        return right;
    }

    public float getBottom() {
        return bottom;
    }
}
