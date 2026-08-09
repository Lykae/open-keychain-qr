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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package org.lykae.keychainqr.ui;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.lykae.keychainqr.Constants;
import org.lykae.keychainqr.R;
import org.lykae.keychainqr.pgp.PgpHelper;
import org.lykae.keychainqr.provider.TemporaryFileProvider;
import org.lykae.keychainqr.ui.base.BaseActivity;
import org.lykae.keychainqr.util.FileHelper;

public class DecryptActivity extends BaseActivity {

    public static final String APPLICATION_AUTOCRYPT_SETUP =
            "application/autocrypt-setup";

    public static final String ACTION_DECRYPT_FROM_CLIPBOARD =
            "DECRYPT_DATA_CLIPBOARD";

    public static final String EXTRA_CLIPDATA =
            "DECRYPT_DATA_CLIPBOARD_DATA";

    public static final String EXTRA_QR_BYTES =
            "DECRYPT_DATA_QR_BYTES";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setFullScreenDialogClose(
                Activity.RESULT_CANCELED,
                false
        );

        handleActions(
                savedInstanceState,
                getIntent()
        );
    }

    @Override
    protected void initLayout() {
        setContentView(R.layout.decrypt_files_activity);
    }

    private void handleActions(
            Bundle savedInstanceState,
            Intent intent) {

        if (savedInstanceState != null) {
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();

        String action = intent.getAction();

        if (action == null) {
            Toast.makeText(
                    this,
                    "Error: No action specified!",
                    Toast.LENGTH_LONG
            ).show();

            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        boolean canDelete = false;
        boolean isAutocryptSetup = false;

        try {

            switch (action) {

                case Intent.ACTION_SEND: {

                    if (intent.hasExtra(Intent.EXTRA_STREAM)) {

                        Uri streamUri =
                                intent.getParcelableExtra(
                                        Intent.EXTRA_STREAM
                                );

                        if (streamUri != null) {
                            uris.add(streamUri);
                        }

                    } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {

                        String text =
                                intent.getStringExtra(
                                        Intent.EXTRA_TEXT
                                );

                        Uri uri =
                                readToTempFile(text);

                        if (uri != null) {
                            uris.add(uri);
                        }
                    }

                    break;
                }

                case Intent.ACTION_SEND_MULTIPLE: {

                    if (intent.hasExtra(Intent.EXTRA_STREAM)) {

                        ArrayList<Uri> streamUris =
                                intent.getParcelableArrayListExtra(
                                        Intent.EXTRA_STREAM
                                );

                        if (streamUris != null) {
                            uris.addAll(streamUris);
                        }

                    } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {

                        ArrayList<String> texts =
                                intent.getStringArrayListExtra(
                                        Intent.EXTRA_TEXT
                                );

                        if (texts != null) {

                            for (String text : texts) {

                                Uri uri =
                                        readToTempFile(text);

                                if (uri != null) {
                                    uris.add(uri);
                                }
                            }
                        }
                    }

                    break;
                }

                case ACTION_DECRYPT_FROM_CLIPBOARD: {

                    ClipData clip = null;

                    if (intent.hasExtra(EXTRA_CLIPDATA)) {

                        clip =
                                intent.getParcelableExtra(
                                        EXTRA_CLIPDATA
                                );
                    }

                    if (clip == null) {
                        break;
                    }

                    Uri uri = null;

                    for (int i = 0;
                         i < clip.getItemCount();
                         i++) {

                        ClipData.Item item =
                                clip.getItemAt(i);

                        Uri itemUri =
                                item.getUri();

                        if (itemUri != null) {
                            uri = itemUri;
                            break;
                        }
                    }

                    if (uri == null) {

                        String text =
                                clip.getItemAt(0)
                                        .coerceToText(this)
                                        .toString();

                        uri =
                                readToTempFile(text);
                    }

                    if (uri != null) {
                        uris.add(uri);
                    }

                    break;
                }

                case Constants.DECRYPT_DATA:
                case Intent.ACTION_VIEW: {

                    if (Intent.ACTION_VIEW.equals(action)) {
                        canDelete = true;
                    }

                    Uri uri =
                            intent.getData();

                    isAutocryptSetup =
                            APPLICATION_AUTOCRYPT_SETUP
                                    .equalsIgnoreCase(
                                            intent.getType()
                                    );

                    if (uri != null) {

                        if ("com.android.email.attachmentprovider"
                                .equals(uri.getHost())) {

                            Toast.makeText(
                                    this,
                                    R.string.error_reading_aosp,
                                    Toast.LENGTH_LONG
                            ).show();

                            finish();
                            return;
                        }

                        uris.add(uri);
                    }

                    break;
                }

                default: {

                    Uri uri =
                            intent.getData();

                    isAutocryptSetup =
                            APPLICATION_AUTOCRYPT_SETUP
                                    .equalsIgnoreCase(
                                            intent.getType()
                                    );

                    if (uri != null) {
                        uris.add(uri);
                    }

                    break;
                }
            }

        } catch (IOException e) {

            Toast.makeText(
                    this,
                    R.string.error_reading_text,
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        if (intent.hasExtra(EXTRA_QR_BYTES)) {

            byte[] qrBytes =
                    intent.getByteArrayExtra(
                            EXTRA_QR_BYTES
                    );

            if (qrBytes == null
                    || qrBytes.length == 0) {

                Toast.makeText(
                        this,
                        "No QR data to decrypt!",
                        Toast.LENGTH_LONG
                ).show();

                setResult(
                        Activity.RESULT_CANCELED
                );

                finish();
                return;
            }

            try {

                Uri qrUri =
                        readToTempFile(qrBytes);

                if (qrUri == null) {

                    Toast.makeText(
                            this,
                            R.string.error_reading_text,
                            Toast.LENGTH_LONG
                    ).show();

                    setResult(
                            Activity.RESULT_CANCELED
                    );

                    finish();
                    return;
                }

                uris.clear();
                uris.add(qrUri);

                canDelete = true;

            } catch (IOException e) {

                Toast.makeText(
                        this,
                        R.string.error_reading_text,
                        Toast.LENGTH_LONG
                ).show();

                setResult(
                        Activity.RESULT_CANCELED
                );

                finish();
                return;
            }
        }

        if (uris.isEmpty()) {

            Toast.makeText(
                    this,
                    "No data to decrypt!",
                    Toast.LENGTH_LONG
            ).show();

            setResult(
                    Activity.RESULT_CANCELED
            );

            finish();
            return;
        }

        displayListFragment(
                uris,
                canDelete,
                isAutocryptSetup
        );
    }
    @Nullable
    public Uri readToTempFile(String text)
            throws IOException {

        if (text == null) {
            return null;
        }

        Uri tempFile =
                TemporaryFileProvider.createFile(this);

        OutputStream outStream =
                FileHelper.openOutputStreamSafe(
                        getContentResolver(),
                        tempFile
                );

        if (outStream == null) {
            return null;
        }

        try {

            // Clean up ASCII armored message.
            String cleanedText =
                    PgpHelper.getPgpMessageContent(text);

            if (cleanedText == null) {
                return null;
            }

            outStream.write(
                    cleanedText.getBytes()
            );

        } finally {
            outStream.close();
        }

        return tempFile;
    }

    @Nullable
    public Uri readToTempFile(byte[] data)
            throws IOException {

        if (data == null || data.length == 0) {
            return null;
        }

        Uri tempFile =
                TemporaryFileProvider.createFile(this);

        OutputStream outStream =
                FileHelper.openOutputStreamSafe(
                        getContentResolver(),
                        tempFile
                );

        if (outStream == null) {
            return null;
        }

        try {

            outStream.write(data);
            outStream.flush();

        } finally {
            outStream.close();
        }

        return tempFile;
    }

    public void displayListFragment(
            ArrayList<Uri> inputUris,
            boolean canDelete,
            boolean isAutocryptSetup) {

        DecryptListFragment frag =
                DecryptListFragment.newInstance(
                        inputUris,
                        canDelete,
                        isAutocryptSetup
                );

        FragmentManager fragMan =
                getSupportFragmentManager();

        FragmentTransaction trans =
                fragMan.beginTransaction();

        trans.replace(
                R.id.decrypt_files_fragment_container,
                frag
        );

        if (fragMan.getFragments() != null
                && !fragMan.getFragments().isEmpty()) {

            trans.addToBackStack("list");
        }

        trans.commit();
    }
}