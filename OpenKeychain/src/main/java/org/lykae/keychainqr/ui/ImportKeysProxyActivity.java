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

import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentActivity;

import com.google.zxing.integration.android.IntentIntegrator;

import org.lykae.keychainqr.Constants;
import org.lykae.keychainqr.R;
import org.lykae.keychainqr.keyimport.ParcelableKeyRing;
import org.lykae.keychainqr.operations.results.ImportKeyResult;
import org.lykae.keychainqr.operations.results.OperationResult;
import org.lykae.keychainqr.operations.results.OperationResult.LogType;
import org.lykae.keychainqr.operations.results.SingletonResult;
import org.lykae.keychainqr.service.ImportKeyringParcel;
import org.lykae.keychainqr.ui.base.CryptoOperationHelper;
import org.lykae.keychainqr.util.IntentIntegratorSupportV4;
import timber.log.Timber;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ImportKeysProxyActivity extends FragmentActivity
        implements CryptoOperationHelper.Callback<ImportKeyringParcel, ImportKeyResult> {

    public static final String ACTION_QR_CODE_API =
            Constants.IMPORT_KEY_FROM_QR_CODE;

    public static final String ACTION_SCAN_WITH_RESULT =
            Constants.INTENT_PREFIX + "SCAN_QR_CODE_WITH_RESULT";

    public static final String ACTION_SCAN_IMPORT =
            Constants.INTENT_PREFIX + "SCAN_QR_CODE_IMPORT";

    public static final String EXTRA_FINGERPRINT = "fingerprint";

    private ArrayList mKeyList;

    private CryptoOperationHelper<ImportKeyringParcel, ImportKeyResult>
            mImportOpHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handleActions(getIntent());
    }

    protected void handleActions(Intent intent) {
        String action = intent.getAction();

        if (ACTION_SCAN_WITH_RESULT.equals(action)
                || ACTION_SCAN_IMPORT.equals(action)
                || ACTION_QR_CODE_API.equals(action)) {

            new IntentIntegrator(this)
                    .setCaptureActivity(QrCodeCaptureActivity.class)
                    .initiateScan();

        } else if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                handleActionNdefDiscovered(intent);
            } else {
                Timber.e("NFC NDEF not supported");
                finish();
            }

        } else {
            Timber.e("No valid action given!");
            finish();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        if (mImportOpHelper != null) {
            if (!mImportOpHelper.handleActivityResult(
                    requestCode,
                    resultCode,
                    data)) {

                if (data != null
                        && data.hasExtra(OperationResult.EXTRA_RESULT)) {

                    returnResult(data);

                } else {
                    super.onActivityResult(requestCode, resultCode, data);
                    finish();
                }
            }
        }

        if (data != null && data.hasExtra("qr_result")) {

            if (resultCode != RESULT_OK) {
                finish();
                return;
            }

            String scannedContent =
                    data.getStringExtra("qr_result");

            if (scannedContent == null
                    || scannedContent.trim().isEmpty()) {

                finish();
                return;
            }

            processScannedContent(scannedContent);
        }
    }

    /**
     * Process the complete ASCII-armored public key from the QR.
     *
     * No fingerprint lookup is performed.
     * No keyserver is contacted.
     */
    private void processScannedContent(String content) {

        Timber.d("Received QR key, length=%d", content.length());

        String keyText = content.trim();

        if (!keyText.contains("-----BEGIN PGP")) {
            SingletonResult result = new SingletonResult(
                    SingletonResult.RESULT_ERROR,
                    LogType.MSG_WRONG_QR_CODE
            );

            Intent intent = new Intent();
            intent.putExtra(
                    SingletonResult.EXTRA_RESULT,
                    result
            );

            returnResult(intent);
            return;
        }

        try {
            byte[] keyBytes =
                    keyText.getBytes(StandardCharsets.UTF_8);

            importKeys(keyBytes);

        } catch (Exception e) {
            Timber.e(e, "Unable to process scanned public key");

            SingletonResult result = new SingletonResult(
                    SingletonResult.RESULT_ERROR,
                    LogType.MSG_WRONG_QR_CODE
            );

            Intent intent = new Intent();
            intent.putExtra(
                    SingletonResult.EXTRA_RESULT,
                    result
            );

            returnResult(intent);
        }
    }

    /**
     * Import the actual key bytes.
     *
     * This is the important difference from
     * createFromReference(): there is no fingerprint
     * and therefore no keyserver lookup.
     */
    public void importKeys(byte[] keyringData) {

        ParcelableKeyRing keyEntry =
                ParcelableKeyRing.createFromEncodedBytes(keyringData);

        ArrayList selectedEntries = new ArrayList<>();
        selectedEntries.add(keyEntry);

        startImportService(selectedEntries);
    }

    private void startImportService(ArrayList keyRings) {

        mKeyList = keyRings;

        mImportOpHelper =
                new CryptoOperationHelper<>(
                        1,
                        this,
                        this,
                        R.string.progress_importing
                );

        mImportOpHelper.cryptoOperation();
    }

    @Override
    public ImportKeyringParcel createOperationInput() {
        return ImportKeyringParcel.createImportKeyringParcel(
                mKeyList,
                null
        );
    }

    @Override
    public void onCryptoOperationSuccess(ImportKeyResult result) {
        Intent data = new Intent();
        data.putExtra(OperationResult.EXTRA_RESULT, result);
        returnResult(data);
    }

    @Override
    public void onCryptoOperationCancelled() {
        finish();
    }

    @Override
    public void onCryptoOperationError(ImportKeyResult result) {

        Bundle returnData = new Bundle();

        returnData.putParcelable(
                OperationResult.EXTRA_RESULT,
                result
        );

        Intent data = new Intent();
        data.putExtras(returnData);

        returnResult(data);
    }

    @Override
    public boolean onCryptoSetProgress(
            String msg,
            int progress,
            int max) {

        return false;
    }

    public void returnResult(Intent data) {

        String action = getIntent().getAction();

        if (ACTION_QR_CODE_API.equals(action)) {

            OperationResult result =
                    data.getParcelableExtra(
                            OperationResult.EXTRA_RESULT
                    );

            if (result != null
                    && result.getLog() != null
                    && result.getLog().getLast() != null) {

                String str = getString(
                        result.getLog()
                                .getLast()
                                .mType
                                .getMsgId()
                );

                Toast.makeText(
                        this,
                        str,
                        Toast.LENGTH_LONG
                ).show();
            }

            finish();

        } else {

            setResult(RESULT_OK, data);
            finish();
        }
    }

    /**
     * NFC already gives us the actual key bytes,
     * so keep this path completely offline too.
     */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    void handleActionNdefDiscovered(Intent intent) {

        Parcelable[] rawMsgs =
                intent.getParcelableArrayExtra(
                        NfcAdapter.EXTRA_NDEF_MESSAGES
                );

        if (rawMsgs == null || rawMsgs.length == 0) {
            finish();
            return;
        }

        NdefMessage msg =
                (NdefMessage) rawMsgs[0];

        byte[] receivedKeyringBytes =
                msg.getRecords()[0].getPayload();

        importKeys(receivedKeyringBytes);
    }
}