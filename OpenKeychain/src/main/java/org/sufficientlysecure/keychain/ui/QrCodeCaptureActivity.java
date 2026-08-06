package org.sufficientlysecure.keychain.ui;

import android.Manifest;
import android.content.Intent;
import android.media.Image;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutionException;

import org.sufficientlysecure.keychain.R;


public class QrCodeCaptureActivity extends AppCompatActivity {

    public static final String EXTRA_SCAN_MODE = "scan_mode";
    public static final int MODE_DECRYPT_MESSAGE = 1;

    private boolean scanned = false;

    private BarcodeScanner scanner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.qr_code_capture_activity);

        PreviewView previewView = findViewById(R.id.preview_view);

        scanner = BarcodeScanning.getClient();

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != getPackageManager().PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    100
            );

        } else {
            startCamera(previewView);
        }
    }


    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera(PreviewView previewView) {

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);


        future.addListener(() -> {

            try {

                ProcessCameraProvider provider = future.get();


                Preview preview =
                        new Preview.Builder()
                                .build();


                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider()
                );


                ImageAnalysis analysis =
                        new ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build();


                analysis.setAnalyzer(
                        ContextCompat.getMainExecutor(this),
                        this::analyze
                );


                provider.unbindAll();


                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                );


            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }


        }, ContextCompat.getMainExecutor(this));
    }

    private void handleBarcodes(java.util.List<Barcode> barcodes) {

        if (scanned || barcodes.isEmpty()) {
            return;
        }

        for (Barcode barcode : barcodes) {

            String value = barcode.getRawValue();

            if (value != null) {

                scanned = true;

                //Toast.makeText(
                //        this,
                //        "QR: " + value,
                //        Toast.LENGTH_LONG
                //).show();

                Intent result = new Intent();
                result.putExtra("qr_result", value);
                setResult(RESULT_OK, result);
                finish();

                break;
            }
        }
    }

    @ExperimentalGetImage
    private void analyze(ImageProxy imageProxy) {

        Image mediaImage = imageProxy.getImage();

        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage input =
                InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.getImageInfo().getRotationDegrees()
                );

        scanner.process(input)
                .addOnSuccessListener(this::handleBarcodes)
                .addOnCompleteListener(task ->
                        imageProxy.close()
                );
    }
}