/*
 *      This file was started before 11:00 20th November, but was not finished.
 *      Before the start time the Tensorflow Lite Interpreter was implemented, but it used a pre-trained model (EfficientNet)
 *      After, at 17:00 the categorization list was introduced to map the labels to the categories.
 *      On 21st November the categorization visual was added
 * */

package com.example.waste;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier.ImageClassifierOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Stream;

import static com.example.waste.MapUtils.sortByValue;
import static java.lang.Integer.max;
import static java.lang.Integer.min;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "WASTE";
    private static final int MAX_CLASSES = 4;

    // UI stuff
    protected TextView buttonTest;
    protected Button categorizeButton;
    protected TextView objectName1;
    protected TextView objectConfidence1;
    protected TextView objectName2;
    protected TextView objectConfidence2;
    protected TextView categoryName;
    protected ImageView categoryIcon;

    //Camera stuff
    protected FrameLayout cameraStream;
    private Camera mCamera;
    private CameraPreview mPreview;
    private int mHeight, mWidth;
    private boolean holderInitialized;
    private boolean isPreviewing;

    //TensorFlow stuff
    private ImageHandling bumpHandler;
    private ImageClassifier imageClassifier;
    private ImageClassifierOptions imageClassifierOptions;
    private boolean isClassifierInit;
    private Classifier classifier;
    private ArrayList<LabeledObject> labeledObjects;
    private Categorizer categorizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        categorizeButton = (Button) findViewById(R.id.button);
        buttonTest = (TextView) findViewById(R.id.textView);
        objectName1 = (TextView) findViewById(R.id.textView1);
        objectConfidence1 = (TextView) findViewById(R.id.textView2);
        objectName2 = (TextView) findViewById(R.id.textView3);
        objectConfidence2 = (TextView) findViewById(R.id.textView4);

        categoryName = (TextView) findViewById(R.id.textView5);
        categoryIcon = (ImageView) findViewById(R.id.imageView);
        categoryName.setVisibility(View.INVISIBLE);
        categoryIcon.setVisibility(View.INVISIBLE);

        cameraStream = (FrameLayout) findViewById(R.id.frameLayout);
        bumpHandler = new ImageHandling(this);
        classifier = new Classifier(this);
        categorizer = new Categorizer(this);

        // Get permission
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            cameraStream.post(new Runnable() {
                @Override
                public void run() {
                    setupCamera();
                }
            });
        } else {
            // You can directly ask for the permission.
            requestPermissions(
                    new String[] { Manifest.permission.CAMERA },
                    1001);
        }
    }

    private void setupCamera() {
        // Create camera instance
        startCamera();
        Camera.PreviewCallback previewCallback = new Camera.PreviewCallback()
        {
            public void onPreviewFrame(byte[] data, Camera camera)
            {
                //Log.d(TAG, "OnPreviewFrame");
                Camera.Parameters parameters = camera.getParameters();
                int imageFormat = parameters.getPreviewFormat();
                if (imageFormat == ImageFormat.NV21)
                {
                    Bitmap outputBitmap = null;
                    try {
                        outputBitmap = bumpHandler.nv21ToBitmap(data, mWidth, mHeight);
                        // Run inference
                        if (imageClassifier != null) {
                            Map<String, Float> result = classifier.runInterference(outputBitmap);
                            if (result != null) {
                                processResults(sortByValue(result));
                            }
                        }
                    }
                    catch (Exception e) {
                        Log.e(TAG, "Can't convert image" + e.toString());
                    }
                }
            }
        };
        mCamera.setPreviewCallback(previewCallback);

        mPreview = new CameraPreview(this, mCamera);
        cameraStream.addView(mPreview);
        holderInitialized = true;
        isPreviewing = true;

        categorizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isPreviewing == true) {
                    stopCamera();
                    buttonTest.setText("Camera stopped");
                    categorizeButton.setText("IDENTIFY");
                    isPreviewing = false;
                    Log.d(TAG, "Close camera");

                    categoryName.setVisibility(View.VISIBLE);
                    categoryIcon.setVisibility(View.VISIBLE);

                    String label = null;
                    try {
                        label = categorizer.getCategory(categorizer.categorizedMap.get((String) objectName1.getText()));
                        categoryName.setText(label);
                    } catch (Exception e) {
                        Log.e(TAG, "Fail to get category: " + e.toString());
                    }
                    try {
                        String generatedFileName = label;// + ".png";
                        categoryIcon.setImageResource(getResources().getIdentifier(label, "drawable", getPackageName()));
                    } catch (Exception e) {
                        Log.e(TAG, "Fail to get .svg: " + e.toString());
                    }
                }
                else {
                    setupCamera();
                    buttonTest.setText("Camera started");
                    categorizeButton.setText("CATEGORIZE");
                    isPreviewing = true;

                    categoryName.setVisibility(View.INVISIBLE);
                    categoryIcon.setVisibility(View.INVISIBLE);
                }
            }
        });

        if (!isClassifierInit) {
            // Initialization
            imageClassifierOptions = ImageClassifierOptions.builder().setMaxResults(2).build();
            imageClassifier = null;
            try {
                imageClassifier = ImageClassifier.createFromFileAndOptions(this,
                        "efficientnet_lite4_fp32_2.tflite", imageClassifierOptions);
                isClassifierInit = true;
            }
            catch (Exception e) {
                Log.e(TAG, "TensorFail " + e.toString());
            }
        }
    }

    private boolean startCamera() {
        try {
            mCamera = Camera.open(0); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            return false;
        }
        // Get images, courtesy of StackOverflow
        // https://stackoverflow.com/questions/3376672/how-to-capture-preview-image-frames-from-camera-application-in-android-programmi
        if (mCamera != null) {
            mCamera.setDisplayOrientation(90);
            Camera.Parameters camParam = mCamera.getParameters();
            List<Camera.Size> previewSizes = camParam.getSupportedPreviewSizes();
            for (Camera.Size size : previewSizes) {
                // 640 480
                // 960 720
                // 1024 768
                // 1280 720
                // 1600 1200
                // 2560 1920
                // 3264 2448
                // 2048 1536
                // 3264 1836
                // 2048 1152
                // 3264 2176
                if (1600 <= size.width & size.width <= 1920) {
                    camParam.setPreviewSize(size.width, size.height);
                    camParam.setPictureSize(size.width, size.height);

                    ViewGroup.LayoutParams layoutParams = cameraStream.getLayoutParams();
                    layoutParams.height = size.height;
                    cameraStream.setLayoutParams(layoutParams);

                    mWidth = size.width;
                    mHeight = size.height;
                    break;
                }
            }
            if (camParam.getSupportedFocusModes().contains(
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                camParam.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            //camParam.setPreviewSize(mWidth, mHeight);
            mCamera.setParameters(camParam);
            if (holderInitialized) {
                try {
                    mCamera.setPreviewDisplay(mPreview.mHolder);
                    mCamera.startPreview();
                }
                catch (Exception e) {
                    return false;
                }
            }
        }
        return mCamera != null;
    }

    private boolean stopCamera() {
        try {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
        }
        catch (Exception e) {
            return false;
        }
        return true;
    }

    private void processResults(Map sortedResult) {
        boolean initialStage = false;
        ArrayList<LabeledObject> bufferObjects = new ArrayList<LabeledObject>();
        if (labeledObjects == null) {
            labeledObjects = new ArrayList<LabeledObject>();
            initialStage = true;
        }
        int counter = 0;
        for (Object key : sortedResult.keySet()) {
            Object value = sortedResult.get(key);
            if (initialStage) {
                labeledObjects.add(new LabeledObject((String) key, (float) value));
            } else {
                bufferObjects.add(new LabeledObject((String) key, (float) value));
            }
            counter++;
            if (counter == MAX_CLASSES) break;
            /*if (counter == 0) {
                Log.i(TAG, "--------");
                break;
            }*/
        }
        for (LabeledObject i : labeledObjects) Log.i(TAG, i.label + "  " + i.score);
        Log.i(TAG, "     ");
        for (LabeledObject i : bufferObjects) Log.i(TAG, i.label + "  " + i.score);
        Log.i(TAG, "     ");
        if (!initialStage) {
            float smoothFactor = 0.6f;
            // Booleans for labeledObjects array
            boolean retainableLabels[] = new boolean[MAX_CLASSES];
            for (boolean i : retainableLabels) i = false;
            // Booleans for bufferObjects array
            boolean repeatedLabels[] = new boolean[MAX_CLASSES];
            for (boolean i : repeatedLabels) i = false;

            for (int i = 0; i < MAX_CLASSES; i++) {
                retainableLabels[i] = false;
                for (int j = 0; j < MAX_CLASSES; j++) {
                    if (labeledObjects.get(i).label.equals(bufferObjects.get(j).label)) {
                        //Log.i(TAG, bufferObjects.get(j).label);
                        retainableLabels[i] = true;
                        repeatedLabels[j] = true;
                        // Smooth out fluctuations
                        labeledObjects.get(i).score += (bufferObjects.get(j).score - labeledObjects.get(i).score) * smoothFactor;
                    }
                }
            }
            for (int i = 0; i < MAX_CLASSES; i++) {
                // Check if there're new labels
                if (!retainableLabels[i]) {
                    // Deprecate old scores
                    labeledObjects.get(i).score *= smoothFactor;
                    // Get max score from new labels
                    int maxIndex = MAX_CLASSES;
                    float maxScore = 0;
                    for (int j = 0; j < MAX_CLASSES; j++) {
                        if (!repeatedLabels[j] && (bufferObjects.get(j).score > maxScore)) {
                            maxScore = bufferObjects.get(j).score;
                            maxIndex = j;
                        }
                    }
                    if ((maxIndex < MAX_CLASSES) && (maxScore >= labeledObjects.get(i).score)) {
                        Log.i(TAG, bufferObjects.get(maxIndex).label + "  " + maxIndex);
                        // Switch labels
                        labeledObjects.get(i).label = bufferObjects.get(maxIndex).label;
                        labeledObjects.get(i).score = bufferObjects.get(maxIndex).score * smoothFactor;
                        repeatedLabels[maxIndex] = true;
                    }
                    Log.i(TAG, "     ");
                }
            }
            Log.i(TAG, "-------------");
            // Sort new list
            Collections.sort(labeledObjects, (l1, l2) -> sgn(l1.score - l2.score));
            Collections.reverse(labeledObjects);
        }

        String label = labeledObjects.get(0).label;
        objectName1.setText(label);
        objectConfidence1.setText(Float.toString((float) labeledObjects.get(0).score * 100) + "%");
        /*try {
            objectConfidence1.setText(categorizer.getCategory(categorizer.categorizedMap.get(label)));
        } catch (Exception e) {
            Log.e(TAG, "Fail to get category: " + e.toString());
        }*/

        label = labeledObjects.get(1).label;
        objectName2.setText(label);
        objectConfidence2.setText(Float.toString((float) labeledObjects.get(1).score * 100) + "%");
        /*try {
            objectConfidence2.setText(categorizer.getCategory(categorizer.categorizedMap.get(label)));
        } catch (Exception e) {
            Log.e(TAG, "Fail to get category: " + e.toString());
        }*/
    }

    private int sgn(float x) {
        if (x > 0) return 1;
        else if (x < 0) return -1;
        else return 0;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        switch (requestCode) {
            case 1001:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission is granted.
                    cameraStream.post(new Runnable() {
                        @Override
                        public void run() {
                            setupCamera();
                        }
                    });
                }
                return;
        }
    }
}