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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.lykae.keychainqr.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.cardview.widget.CardView;
import android.widget.ImageView;

import org.lykae.keychainqr.R;
import org.lykae.keychainqr.ui.base.BaseActivity;
import org.lykae.keychainqr.ui.util.Notify;
import org.lykae.keychainqr.ui.util.QrCodeUtils;

public class QrCodeViewActivity extends BaseActivity {

    public static final String EXTRA_TEXT = "text";

    private ImageView qrCodeImageView;
    private Bitmap qrCode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setFullScreenDialogClose(v ->
                ActivityCompat.finishAfterTransition(QrCodeViewActivity.this));

        qrCodeImageView = findViewById(R.id.qr_code_image);

        CardView qrCodeLayout = findViewById(R.id.qr_code_image_layout);
        qrCodeLayout.setOnClickListener(v ->
                ActivityCompat.finishAfterTransition(QrCodeViewActivity.this));

        String armoredKey = getIntent().getStringExtra(EXTRA_TEXT);

        if (armoredKey == null || armoredKey.trim().isEmpty()) {
            Notify.create(this, "No public key supplied", Notify.Style.ERROR).show();
            finish();
            return;
        }

        try {
            // The QR contains the actual ASCII-armored OpenPGP public key.
            qrCode = QrCodeUtils.getQRCodeBitmap(armoredKey, 0);

            qrCodeImageView.post(() -> {
                if (qrCode == null || qrCodeImageView.getWidth() <= 0) {
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
                    "Public key is too large for a single QR code",
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