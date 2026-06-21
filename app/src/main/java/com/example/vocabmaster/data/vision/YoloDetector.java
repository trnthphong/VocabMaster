package com.example.vocabmaster.data.vision;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class YoloDetector implements AutoCloseable {
    public static final String MODEL_FILE = "yolo11n_float32.tflite";
    private static final String LABEL_FILE = "coco_labels.txt";
    private static final int INPUT_SIZE = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.35f;
    private static final float IOU_THRESHOLD = 0.45f;
    private static final int MAX_RESULTS = 20;

    private final Interpreter interpreter;
    private final List<String> labels;

    public YoloDetector(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(loadModel(context), options);
        labels = loadLabels(context);
    }

    public List<YoloDetection> detect(Bitmap source) {
        Bitmap inputBitmap = letterbox(source, INPUT_SIZE, INPUT_SIZE);
        float[][][][] input = bitmapToFloatInput(inputBitmap);

        int[] shape = interpreter.getOutputTensor(0).shape();
        if (shape.length != 3) return Collections.emptyList();

        float[][][] output = new float[shape[0]][shape[1]][shape[2]];
        interpreter.run(input, output);

        List<YoloDetection> detections = parseOutput(output[0], shape, source.getWidth(), source.getHeight());
        return nonMaxSuppression(detections);
    }

    private MappedByteBuffer loadModel(Context context) throws IOException {
        AssetFileDescriptor descriptor = context.getAssets().openFd(MODEL_FILE);
        try (FileInputStream inputStream = new FileInputStream(descriptor.getFileDescriptor());
             FileChannel channel = inputStream.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, descriptor.getStartOffset(), descriptor.getDeclaredLength());
        }
    }

    private List<String> loadLabels(Context context) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(LABEL_FILE)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String label = line.trim();
                if (!label.isEmpty()) result.add(label);
            }
        }
        return result;
    }

    private Bitmap letterbox(Bitmap source, int width, int height) {
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.rgb(114, 114, 114));

        float scale = Math.min(width / (float) source.getWidth(), height / (float) source.getHeight());
        float scaledWidth = source.getWidth() * scale;
        float scaledHeight = source.getHeight() * scale;
        float left = (width - scaledWidth) / 2f;
        float top = (height - scaledHeight) / 2f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new RectF(left, top, left + scaledWidth, top + scaledHeight), paint);
        return output;
    }

    private float[][][][] bitmapToFloatInput(Bitmap bitmap) {
        float[][][][] input = new float[1][INPUT_SIZE][INPUT_SIZE][3];
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        int index = 0;
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = pixels[index++];
                input[0][y][x][0] = Color.red(pixel) / 255f;
                input[0][y][x][1] = Color.green(pixel) / 255f;
                input[0][y][x][2] = Color.blue(pixel) / 255f;
            }
        }
        return input;
    }

    private List<YoloDetection> parseOutput(float[][] raw, int[] shape, int imageWidth, int imageHeight) {
        int rows = shape[1];
        int cols = shape[2];
        boolean transposed = rows < cols;
        int candidates = transposed ? cols : rows;
        int values = transposed ? rows : cols;

        List<YoloDetection> detections = new ArrayList<>();
        for (int i = 0; i < candidates; i++) {
            float cx = value(raw, transposed, i, 0);
            float cy = value(raw, transposed, i, 1);
            float w = value(raw, transposed, i, 2);
            float h = value(raw, transposed, i, 3);

            int bestClass = -1;
            float bestScore = 0f;
            int classStart = values == labels.size() + 5 ? 5 : 4;
            for (int c = classStart; c < values && c - classStart < labels.size(); c++) {
                float score = value(raw, transposed, i, c);
                if (score > bestScore) {
                    bestScore = score;
                    bestClass = c - classStart;
                }
            }

            if (bestClass < 0 || bestScore < CONFIDENCE_THRESHOLD) continue;

            float left = clamp((cx - w / 2f) / INPUT_SIZE * imageWidth, 0, imageWidth);
            float top = clamp((cy - h / 2f) / INPUT_SIZE * imageHeight, 0, imageHeight);
            float right = clamp((cx + w / 2f) / INPUT_SIZE * imageWidth, 0, imageWidth);
            float bottom = clamp((cy + h / 2f) / INPUT_SIZE * imageHeight, 0, imageHeight);

            detections.add(new YoloDetection(labels.get(bestClass), bestScore, left, top, right, bottom));
        }
        return detections;
    }

    private float value(float[][] raw, boolean transposed, int candidate, int index) {
        return transposed ? raw[index][candidate] : raw[candidate][index];
    }

    private List<YoloDetection> nonMaxSuppression(List<YoloDetection> detections) {
        detections.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        List<YoloDetection> selected = new ArrayList<>();
        Set<String> usedLabels = new HashSet<>();

        for (YoloDetection detection : detections) {
            boolean overlaps = false;
            for (YoloDetection kept : selected) {
                if (detection.getLabel().equals(kept.getLabel()) && iou(detection, kept) > IOU_THRESHOLD) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps && !usedLabels.contains(detection.getLabel())) {
                selected.add(detection);
                usedLabels.add(detection.getLabel());
            }
            if (selected.size() >= MAX_RESULTS) break;
        }
        return selected;
    }

    private float iou(YoloDetection a, YoloDetection b) {
        float left = Math.max(a.getLeft(), b.getLeft());
        float top = Math.max(a.getTop(), b.getTop());
        float right = Math.min(a.getRight(), b.getRight());
        float bottom = Math.min(a.getBottom(), b.getBottom());
        float intersection = Math.max(0, right - left) * Math.max(0, bottom - top);
        float areaA = Math.max(0, a.getRight() - a.getLeft()) * Math.max(0, a.getBottom() - a.getTop());
        float areaB = Math.max(0, b.getRight() - b.getLeft()) * Math.max(0, b.getBottom() - b.getTop());
        return intersection / Math.max(1f, areaA + areaB - intersection);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void close() {
        interpreter.close();
    }
}
