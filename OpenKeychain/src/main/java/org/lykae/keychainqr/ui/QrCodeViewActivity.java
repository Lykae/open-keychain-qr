/*
* Copyright (C) 2017 Schürmann & Breitmoser GbR
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see http://www.gnu.org/licenses/.
*/

package org.lykae.keychainqr.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import org.lykae.keychainqr.R;
import org.lykae.keychainqr.ui.base.BaseActivity;
import org.lykae.keychainqr.ui.util.Notify;
import org.lykae.keychainqr.ui.util.QrCodeUtils;

public class QrCodeViewActivity extends BaseActivity {

    /**
     * Legacy String extra.
     */
    public static final String EXTRA_TEXT = "text";

    /**
     * Binary QR payload.
     */
    public static final String EXTRA_BYTES = "bytes";

    private ImageView qrCodeImageView;
    private Bitmap qrCode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setFullScreenDialogClose(v ->
                ActivityCompat.finishAfterTransition(QrCodeViewActivity.this));

        qrCodeImageView = findViewById(R.id.qr_code_image);

        CardView qrCodeLayout =
                findViewById(R.id.qr_code_image_layout);

        qrCodeLayout.setOnClickListener(v ->
                ActivityCompat.finishAfterTransition(QrCodeViewActivity.this));

        /*
         * Prefer binary data.
         */
        byte[] data = getIntent().getByteArrayExtra(EXTRA_BYTES);

        /*
         * Keep backwards compatibility with callers that still send
         * EXTRA_TEXT.
         */
        if (data == null || data.length == 0) {
            String text = getIntent().getStringExtra(EXTRA_TEXT);

            if (text != null && !text.trim().isEmpty()) {
                data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        if (data == null || data.length == 0) {
            Notify.create(
                    this,
                    "No data supplied",
                    Notify.Style.ERROR
            ).show();

            finish();
            return;
        }

        try {
            qrCode = QrCodeUtils.getQRCodeBitmap(data, 0);

            if (qrCode == null) {
                throw new IllegalStateException("QR code generation failed");
            }

            final byte[] qrData = data;

            qrCodeImageView.post(() -> {
                if (qrCode == null
                        || qrCodeImageView.getWidth() <= 0) {
                    return;
                }

                Bitmap scaled = Bitmap.createScaledBitmap(
                        qrCode,
                        qrCodeImageView.getWidth(),
                        qrCodeImageView.getWidth(),
                        false
                );

                qrCodeImageView.setImageBitmap(scaled);
            });

        } catch (Exception e) {
            Notify.create(
                    this,
                    "Data is too large for a single QR code",
                    Notify.Style.ERROR
            ).show();

            finish();
        }
    }

    @Override
    protected void initLayout() {
        setContentView(R.layout.qr_code_activity);
    }
}