package org.lykae.keychainqr.ui;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.media.Image;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.lykae.keychainqr.R;

public class QrCodeCaptureActivity extends AppCompatActivity {

    public static final String EXTRA_SCAN_MODE = "scan_mode";
    public static final int MODE_DECRYPT_MESSAGE = 1;

    /*
     * Binary QR result.
     */
    public static final String EXTRA_QR_RESULT_BYTES =
            "qr_result_bytes";

    private static final int PICK_IMAGE_REQUEST = 200;
    private static final int CAMERA_REQUEST = 100;

    private BarcodeScanner scanner;

    private boolean scanned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.qr_code_capture_activity);

        BarcodeScannerOptions options =
                new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                                Barcode.FORMAT_QR_CODE
                        )
                        .build();

        scanner =
                BarcodeScanning.getClient(options);

        PreviewView previewView =
                findViewById(R.id.preview_view);

        Button galleryButton =
                findViewById(R.id.gallery_button);

        galleryButton.setOnClickListener(
                v -> openGallery()
        );

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != getPackageManager().PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA
                    },
                    CAMERA_REQUEST
            );

        } else {

            startCamera(previewView);
        }
    }

    /**
     * Open Android gallery/file picker.
     */
    private void openGallery() {

        Intent intent =
                new Intent(Intent.ACTION_GET_CONTENT);

        intent.setType("image/*");

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        startActivityForResult(
                Intent.createChooser(
                        intent,
                        "Select QR image"
                ),
                PICK_IMAGE_REQUEST
        );
    }

    /**
     * Receive the selected gallery image.
     */
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != PICK_IMAGE_REQUEST) {
            return;
        }

        if (resultCode != RESULT_OK
                || data == null) {

            Toast.makeText(
                    this,
                    "No image selected",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Uri uri =
                data.getData();

        if (uri == null) {

            Toast.makeText(
                    this,
                    "Unable to open image",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Toast.makeText(
                this,
                "Image selected",
                Toast.LENGTH_SHORT
        ).show();

        scanImage(uri);
    }

    private void scanImage(Uri uri) {

        Toast.makeText(
                this,
                "Loading image...",
                Toast.LENGTH_SHORT
        ).show();

        try {

            Bitmap bitmap =
                    MediaStore.Images.Media.getBitmap(
                            getContentResolver(),
                            uri
                    );

            Toast.makeText(
                    this,
                    "Image loaded: "
                            + bitmap.getWidth()
                            + "x"
                            + bitmap.getHeight(),
                    Toast.LENGTH_LONG
            ).show();

            bitmap =
                    resizeBitmap(bitmap);

            scanWithAutoCrop(bitmap);

            scanBitmapGrid(bitmap);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Image error: "
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private Bitmap resizeBitmap(
            Bitmap bitmap) {

        int maxSize = 1600;

        int width =
                bitmap.getWidth();

        int height =
                bitmap.getHeight();

        float scale =
                Math.min(
                        (float) maxSize / width,
                        (float) maxSize / height
                );

        if (scale >= 1) {
            return bitmap;
        }

        return Bitmap.createScaledBitmap(
                bitmap,
                (int) (width * scale),
                (int) (height * scale),
                true
        );
    }

    private void scanBitmap(
            Bitmap bitmap) {

        if (scanned) {
            return;
        }

        InputImage image =
                InputImage.fromBitmap(
                        bitmap,
                        0
                );

        scanner.process(image)
                .addOnSuccessListener(
                        barcodes -> {

                            if (!barcodes.isEmpty()) {
                                handleBarcodes(
                                        barcodes
                                );
                            }
                        }
                )
                .addOnFailureListener(
                        e -> Toast.makeText(
                                this,
                                "Scan error: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }

    private void scanWithAutoCrop(
            Bitmap bitmap) {

        if (scanned) {
            return;
        }

        /*
         * Full image.
         */
        scanBitmap(bitmap);

        if (scanned) {
            return;
        }

        /*
         * Center crop.
         */
        int width =
                bitmap.getWidth();

        int height =
                bitmap.getHeight();

        int cropSize =
                Math.min(
                        width,
                        height
                );

        Bitmap centerCrop =
                Bitmap.createBitmap(
                        bitmap,
                        (width - cropSize) / 2,
                        (height - cropSize) / 2,
                        cropSize,
                        cropSize
                );

        scanBitmap(centerCrop);
    }

    private void scanBitmapGrid(
            Bitmap bitmap) {

        if (scanned) {
            return;
        }

        int width =
                bitmap.getWidth();

        int height =
                bitmap.getHeight();

        int cols = 3;
        int rows = 3;

        int cropWidth =
                width / cols;

        int cropHeight =
                height / rows;

        for (int y = 0;
             y < rows && !scanned;
             y++) {

            for (int x = 0;
                 x < cols && !scanned;
                 x++) {

                Bitmap crop =
                        Bitmap.createBitmap(
                                bitmap,
                                x * cropWidth,
                                y * cropHeight,
                                cropWidth,
                                cropHeight
                        );

                scanBitmap(crop);
            }
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera(
            PreviewView previewView) {

        ListenableFuture<ProcessCameraProvider>
                future =
                ProcessCameraProvider
                        .getInstance(this);

        future.addListener(
                () -> {

                    try {

                        ProcessCameraProvider provider =
                                future.get();

                        Preview preview =
                                new Preview.Builder()
                                        .build();

                        preview.setSurfaceProvider(
                                previewView
                                        .getSurfaceProvider()
                        );

                        ImageAnalysis analysis =
                                new ImageAnalysis.Builder()
                                        .setBackpressureStrategy(
                                                ImageAnalysis
                                                        .STRATEGY_KEEP_ONLY_LATEST
                                        )
                                        .build();

                        analysis.setAnalyzer(
                                ContextCompat
                                        .getMainExecutor(this),
                                this::analyze
                        );

                        provider.unbindAll();

                        provider.bindToLifecycle(
                                this,
                                CameraSelector
                                        .DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                        );

                    } catch (
                            ExecutionException |
                            InterruptedException e) {

                        Toast.makeText(
                                this,
                                "Scanner error: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();

                        finish();
                    }

                },
                ContextCompat.getMainExecutor(this)
        );
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(
            ImageProxy imageProxy) {

        if (scanned) {
            imageProxy.close();
            return;
        }

        Image mediaImage =
                imageProxy.getImage();

        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage image =
                InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy
                                .getImageInfo()
                                .getRotationDegrees()
                );

        scanner.process(image)
                .addOnSuccessListener(
                        this::handleBarcodes
                )
                .addOnCompleteListener(
                        task ->
                                imageProxy.close()
                );
    }

    /**
     * Return QR data as bytes.
     *
     * Do NOT use getRawValue() for binary
     * OpenPGP QR data.
     */
    private void handleBarcodes(
            List<Barcode> barcodes) {

        if (scanned) {
            return;
        }

        for (Barcode barcode : barcodes) {

            byte[] value =
                    barcode.getRawBytes();

            if (value == null
                    || value.length == 0) {

                continue;
            }

            scanned = true;

            Intent result =
                    new Intent();

            result.putExtra(
                    EXTRA_QR_RESULT_BYTES,
                    value
            );

            setResult(
                    RESULT_OK,
                    result
            );

            finish();

            return;
        }
    }

    @Override
    protected void onDestroy() {

        if (scanner != null) {
            scanner.close();
        }

        super.onDestroy();
    }
}