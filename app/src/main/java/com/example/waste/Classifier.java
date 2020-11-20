/*
 *      This file was started before 11:00 20th November, but was not finished.
 *      Before the start time the file was tailored to the pre-trained model (EfficientNet)
 * */

package com.example.waste;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.Log;

import com.example.waste.ml.EfficientnetLite4Fp322;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.nio.MappedByteBuffer;
import java.util.List;
import java.util.Map;

public class Classifier {
    private static final String TAG = "WASTE";
    // processHeight and processWidth denote input image size
    private static final int processHeight = 300;
    private static final int processWidth = 300;
    // dataType denotes input image type
    private static final DataType dataType = DataType.FLOAT32;
    private static final String modelPath = "efficientnet_lite4_fp32_2.tflite";
    private static final String ASSOCIATED_AXIS_LABELS = "labels_without_background.txt";
    private static final float processMean = 127.5f;
    private static final float processSTD = 127.5f;

    // Useful objects
    private ImageProcessor imageProcessor;
    private TensorImage bufferImage;
    private TensorBuffer outputBuffer;
    private MappedByteBuffer modelBuffer;
    private Interpreter model;
    // Post-processing label apply
    private TensorProcessor outputProcessor;
    private List<String> associatedAxisLabels;

    // Testing
    private EfficientnetLite4Fp322 modelTest;

    public Classifier(Activity activity) {
        try {
            modelTest = EfficientnetLite4Fp322.newInstance(activity);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load model: " + e.toString());
        }

        // Initialize the stuff
        bufferImage = new TensorImage(dataType);
        outputBuffer = TensorBuffer.createFixedSize(new int[]{1, 1000}, dataType);
        try {
            modelBuffer = FileUtil.loadMappedFile(activity, modelPath);
            model = new Interpreter(modelBuffer);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load tflite: " + e.toString());
        }
        associatedAxisLabels = null;
        try {
            associatedAxisLabels = FileUtil.loadLabels(activity, ASSOCIATED_AXIS_LABELS);
            outputProcessor = new TensorProcessor.Builder().add(new NormalizeOp(0, 1.0f)).build();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load label file: " + e.toString());
        }
    }

    public List<Category> runInterferenceTest(Bitmap bitmap) {
        bufferImage.load(bitmap);
        // Get largest square
        int size = Math.min(bitmap.getHeight(), bitmap.getWidth());
        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeWithCropOrPadOp(size, size))
                .add(new ResizeOp(processHeight, processWidth, ResizeOp.ResizeMethod.BILINEAR))
                .build();
        bufferImage = imageProcessor.process(bufferImage);
        try {
            EfficientnetLite4Fp322.Outputs outputs = modelTest.process(bufferImage);
            return outputs.getProbabilityAsCategoryList();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get results: " + e.toString());
        }
        return null;
    }

    public Map<String, Float> runInterference(Bitmap bitmap) {
        if (modelBuffer != null) {
            //bufferImage = TensorImage.fromBitmap(bitmap);
            bufferImage.load(bitmap);
            // Get largest square
            int size = Math.min(bitmap.getHeight(), bitmap.getWidth());
            imageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeWithCropOrPadOp(size, size))
                    .add(new ResizeOp(processHeight, processWidth, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(processMean, processSTD))
                    .build();
            try {
                bufferImage = imageProcessor.process(bufferImage);
                try {
                    model.run(bufferImage.getBuffer(), outputBuffer.getBuffer().rewind());

                    if (associatedAxisLabels != null) {
                        Map<String, Float> floatMap = new TensorLabel(associatedAxisLabels, outputProcessor.process(outputBuffer)).getMapWithFloatValue();
                        return floatMap;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Run fails: " + e.toString());
                }
            } catch (Exception e) {
                Log.e(TAG, "Processing fails: " + e.toString());
            }
        }
        return null;
    }
}

