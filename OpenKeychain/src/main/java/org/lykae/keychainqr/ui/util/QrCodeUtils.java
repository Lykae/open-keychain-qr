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
* along with this program.  If not, see
* <http://www.gnu.org/licenses/>.
*/

package org.lykae.keychainqr.ui.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.lykae.keychainqr.KeychainApplication;

import java.nio.charset.Charset;
import java.util.Hashtable;

import timber.log.Timber;

/**
 * Copied from Bitcoin Wallet
 */
public class QrCodeUtils {

    /*
     * ISO-8859-1 gives a 1:1 mapping between Java chars and byte values:
     *
     *   byte 0x00 -> char U+0000
     *   byte 0xFF -> char U+00FF
     *
     * This lets us pass arbitrary binary data through ZXing's String API
     * without changing the actual bytes that are placed into the QR code.
     */
    private static final Charset QR_BYTE_CHARSET =
            Charset.forName("ISO-8859-1");

    public static Bitmap getQRCodeBitmap(final Uri uri) {
        return getQRCodeBitmap(uri.toString(), 0);
    }

    public static Bitmap getQRCodeBitmap(
            final Uri uri,
            final int size) {

        return getQRCodeBitmap(uri.toString(), size);
    }

    /**
     * Generate QR code from text.
     */
    public static Bitmap getQRCodeBitmap(
            final String input,
            final int size) {

        if (input == null) {
            return null;
        }

        try {

            /*
             * Keep the existing String cache behavior.
             */
            Bitmap bitmap =
                    KeychainApplication.qrCodeCache.get(input);

            if (bitmap == null) {

                Hashtable<EncodeHintType, Object> hints =
                        new Hashtable<>();

                hints.put(
                        EncodeHintType.ERROR_CORRECTION,
                        ErrorCorrectionLevel.Q
                );

                BitMatrix result =
                        new QRCodeWriter().encode(
                                input,
                                BarcodeFormat.QR_CODE,
                                size,
                                size,
                                hints
                        );

                bitmap = bitMatrixToBitmap(result);

                KeychainApplication.qrCodeCache.put(
                        input,
                        bitmap
                );
            }

            return bitmap;

        } catch (WriterException e) {

            Timber.e(e, "QrCodeUtils");

            return null;
        }
    }

    /**
     * Generate QR code directly from binary data.
     *
     * The supplied bytes are encoded as QR BYTE data.
     *
     * No ASCII armor, Base64, hexadecimal encoding, or UTF-8
     * expansion is performed.
     */
    public static Bitmap getQRCodeBitmap(
            final byte[] input,
            final int size) {

        if (input == null || input.length == 0) {
            return null;
        }

        try {

            /*
             * Convert bytes to a String using ISO-8859-1.
             *
             * This is NOT an ASCII/Base64 conversion.
             *
             * ISO-8859-1 maps every possible byte value directly
             * to one Java character. ZXing then encodes that String
             * using the same ISO-8859-1 charset, recovering the
             * original bytes for the QR BYTE segment.
             */
            String binaryString =
                    new String(
                            input,
                            QR_BYTE_CHARSET
                    );

            Hashtable<EncodeHintType, Object> hints =
                    new Hashtable<>();

            hints.put(
                    EncodeHintType.ERROR_CORRECTION,
                    ErrorCorrectionLevel.Q
            );

            /*
             * This is the important part.
             *
             * ZXing will use the specified charset when creating
             * the QR byte segment instead of converting the data
             * through UTF-8.
             */
            hints.put(
                    EncodeHintType.CHARACTER_SET,
                    "ISO-8859-1"
            );

            BitMatrix result =
                    new QRCodeWriter().encode(
                            binaryString,
                            BarcodeFormat.QR_CODE,
                            size,
                            size,
                            hints
                    );

            return bitMatrixToBitmap(result);

        } catch (WriterException e) {

            Timber.e(
                    e,
                    "Unable to encode binary QR data"
            );

            return null;
        }
    }

    /**
     * Convert ZXing BitMatrix to Android Bitmap.
     */
    private static Bitmap bitMatrixToBitmap(
            final BitMatrix result) {

        int width = result.getWidth();
        int height = result.getHeight();

        int[] pixels =
                new int[width * height];

        for (int y = 0; y < height; y++) {

            final int offset = y * width;

            for (int x = 0; x < width; x++) {

                pixels[offset + x] =
                        result.get(x, y)
                                ? Color.BLACK
                                : Color.WHITE;
            }
        }

        Bitmap bitmap =
                Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888
                );

        bitmap.setPixels(
                pixels,
                0,
                width,
                0,
                0,
                width,
                height
        );

        return bitmap;
    }
}