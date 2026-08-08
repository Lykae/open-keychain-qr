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

import android.app.Activity;
import android.app.ActivityOptions;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProviders;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.cardview.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.lykae.keychainqr.Constants;
import org.lykae.keychainqr.R;
import org.lykae.keychainqr.daos.KeyRepository;
import org.lykae.keychainqr.daos.KeyRepository.NotFoundException;
import org.lykae.keychainqr.livedata.GenericLiveData;
import org.lykae.keychainqr.model.UnifiedKeyInfo;
import org.lykae.keychainqr.ui.QrCodeViewActivity;
import org.lykae.keychainqr.ui.ViewKeyAdvActivity.ViewKeyAdvViewModel;
import org.lykae.keychainqr.ui.util.KeyFormattingUtils;
import org.lykae.keychainqr.ui.util.Notify;
import org.lykae.keychainqr.ui.util.Notify.Style;
import org.lykae.keychainqr.ui.util.QrCodeUtils;
import org.lykae.keychainqr.util.ShareKeyHelper;

import java.io.IOException;

public class ViewKeyAdvShareFragment extends Fragment {

    private ImageView mQrCode;
    private CardView mQrCodeLayout;
    private TextView mFingerprintView;

    private Bitmap mQrCodeBitmapCache;
    private UnifiedKeyInfo unifiedKeyInfo;

    /*
     * The complete ASCII-armored public key used by the QR code.
     *
     * This is deliberately kept separate from the fingerprint. The QR code
     * contains the actual public key so that the receiving device can import
     * it without contacting a keyserver.
     */
    private String mQrCodeText;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup viewGroup,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.view_key_adv_share_fragment,
                viewGroup,
                false
        );

        mFingerprintView = view.findViewById(R.id.view_key_fingerprint);
        mQrCode = view.findViewById(R.id.view_key_qr_code);

        /*
         * We cache the QR bitmap at its smallest possible size and scale it
         * when the ImageView changes size.
         */
        mQrCode.addOnLayoutChangeListener(
                (v, left, top, right, bottom,
                 oldLeft, oldTop, oldRight, oldBottom) -> {

                    if (mQrCodeBitmapCache == null) {
                        return;
                    }

                    int width = mQrCode.getWidth();
                    int height = mQrCode.getHeight();

                    if (width <= 0 || height <= 0) {
                        return;
                    }

                    Bitmap scaled = Bitmap.createScaledBitmap(
                            mQrCodeBitmapCache,
                            width,
                            height,
                            false
                    );

                    mQrCode.setImageBitmap(scaled);
                }
        );

        mQrCodeLayout = view.findViewById(R.id.view_key_qr_code_layout);
        mQrCodeLayout.setOnClickListener(v -> showQrCodeDialog());

        View vFingerprintShareButton =
                view.findViewById(R.id.view_key_action_fingerprint_share);

        View vFingerprintClipboardButton =
                view.findViewById(R.id.view_key_action_fingerprint_clipboard);

        View vKeyShareButton =
                view.findViewById(R.id.view_key_action_key_share);

        View vKeyClipboardButton =
                view.findViewById(R.id.view_key_action_key_clipboard);

        View vKeySshShareButton =
                view.findViewById(R.id.view_key_action_key_ssh_share);

        View vKeySshClipboardButton =
                view.findViewById(R.id.view_key_action_key_ssh_clipboard);

        /*
         * Keyserver functionality has intentionally been removed.
         *
         * Hide the old upload button if it still exists in the layout.
         */
        View vKeyUploadButton =
                view.findViewById(R.id.view_key_action_upload);

        if (vKeyUploadButton != null) {
            vKeyUploadButton.setVisibility(View.GONE);
        }

        vFingerprintShareButton.setOnClickListener(
                v -> shareFingerprint(false)
        );

        vFingerprintClipboardButton.setOnClickListener(
                v -> shareFingerprint(true)
        );

        /*
         * These share the complete ASCII-armored public key.
         */
        vKeyShareButton.setOnClickListener(
                v -> {
                    if (unifiedKeyInfo != null) {
                        ShareKeyHelper.shareKey(
                                getActivity(),
                                unifiedKeyInfo.master_key_id()
                        );
                    }
                }
        );

        vKeyClipboardButton.setOnClickListener(
                v -> {
                    if (unifiedKeyInfo != null) {
                        ShareKeyHelper.shareKeyToClipboard(
                                getActivity(),
                                unifiedKeyInfo.master_key_id()
                        );
                    }
                }
        );

        vKeySshShareButton.setOnClickListener(
                v -> {
                    if (unifiedKeyInfo != null) {
                        ShareKeyHelper.shareSshKey(
                                getActivity(),
                                unifiedKeyInfo.master_key_id()
                        );
                    }
                }
        );

        vKeySshClipboardButton.setOnClickListener(
                v -> {
                    if (unifiedKeyInfo != null) {
                        ShareKeyHelper.shareSshKeyToClipboard(
                                getActivity(),
                                unifiedKeyInfo.master_key_id()
                        );
                    }
                }
        );

        return view;
    }

    private void shareFingerprint(boolean toClipboard) {
        Activity activity = getActivity();

        if (activity == null || unifiedKeyInfo == null) {
            return;
        }

        String fingerprint =
                KeyFormattingUtils.convertFingerprintToHex(
                        unifiedKeyInfo.fingerprint()
                );

        String content;

        if (toClipboard) {
            content = fingerprint;
        } else {
            content = Constants.FINGERPRINT_SCHEME + ":" + fingerprint;
        }

        if (toClipboard) {
            ClipboardManager clipMan =
                    (ClipboardManager) activity.getSystemService(
                            Context.CLIPBOARD_SERVICE
                    );

            if (clipMan == null) {
                Notify.create(
                        activity,
                        R.string.error_clipboard_copy,
                        Style.ERROR
                ).show();
                return;
            }

            ClipData clip = ClipData.newPlainText(
                    Constants.CLIPBOARD_LABEL,
                    content
            );

            clipMan.setPrimaryClip(clip);

            Notify.create(
                    activity,
                    R.string.fingerprint_copied_to_clipboard,
                    Style.OK
            ).show();

            return;
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, content);
        sendIntent.setType("text/plain");

        String title =
                getString(R.string.title_share_fingerprint_with);

        Intent shareChooser =
                Intent.createChooser(sendIntent, title);

        startActivity(shareChooser);
    }

    /**
     * Opens the QR code viewer with the COMPLETE ASCII-armored public key.
     *
     * Previously this passed only EXTRA_MASTER_KEY_ID, causing
     * QrCodeViewActivity to generate a fingerprint QR.
     *
     * Now we pass EXTRA_TEXT, which QrCodeViewActivity already supports.
     */
    private void showQrCodeDialog() {
        Activity activity = getActivity();

        if (activity == null || mQrCodeText == null || mQrCodeText.isEmpty()) {
            return;
        }

        Intent qrCodeIntent =
                new Intent(activity, QrCodeViewActivity.class);

        /*
         * Tell QrCodeViewActivity to encode the actual armored key rather
         * than looking up the key by master key ID and generating a
         * fingerprint QR.
         */
        qrCodeIntent.putExtra(
                QrCodeViewActivity.EXTRA_TEXT,
                mQrCodeText
        );

        Bundle opts = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityOptions options =
                    ActivityOptions.makeSceneTransitionAnimation(
                            activity,
                            mQrCodeLayout,
                            "qr_code"
                    );

            opts = options.toBundle();
        }

        ActivityCompat.startActivity(
                activity,
                qrCodeIntent,
                opts
        );
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        ViewKeyAdvViewModel viewModel =
                ViewModelProviders.of(requireActivity())
                        .get(ViewKeyAdvViewModel.class);

        LiveData<UnifiedKeyInfo> unifiedKeyInfoLiveData =
                viewModel.getUnifiedKeyInfoLiveData(requireContext());

        unifiedKeyInfoLiveData.observe(
                getViewLifecycleOwner(),
                this::onLoadUnifiedKeyInfo
        );

        /*
         * Generate the QR from the local public key.
         *
         * No keyserver lookup is performed here.
         */
        LiveData<Bitmap> qrCodeLiveData =
                Transformations.switchMap(
                        unifiedKeyInfoLiveData,
                        keyInfo -> {

                            if (keyInfo == null) {
                                return null;
                            }

                            return new GenericLiveData<>(
                                    getContext(),
                                    () -> {

                                        try {
                                            KeyRepository repository =
                                                    KeyRepository.create(
                                                            requireContext()
                                                    );

                                            /*
                                             * Export the locally stored
                                             * public key as ASCII armor.
                                             */
                                            String armoredKey =
                                                    repository
                                                            .getPublicKeyRingAsArmoredString(
                                                                    keyInfo.master_key_id()
                                                            );

                                            if (armoredKey == null ||
                                                    armoredKey.isEmpty()) {
                                                return null;
                                            }

                                            /*
                                             * Save the exact text used by
                                             * the QR viewer.
                                             */
                                            mQrCodeText = armoredKey;

                                            /*
                                             * Encode the COMPLETE armored
                                             * public key into one QR.
                                             *
                                             * If the key is too large,
                                             * QrCodeUtils will throw and the
                                             * error is handled below.
                                             */
                                            return QrCodeUtils.getQRCodeBitmap(
                                                    armoredKey,
                                                    0
                                            );

                                        } catch (NotFoundException e) {
                                            return null;
                                        } catch (IOException e) {
                                            return null;
                                        } catch (RuntimeException e) {
                                            /*
                                             * Usually means the data is too
                                             * large for a single QR code.
                                             */
                                            return null;
                                        }
                                    }
                            );
                        }
                );

        qrCodeLiveData.observe(
                getViewLifecycleOwner(),
                this::onLoadQrCode
        );
    }

    public void onLoadUnifiedKeyInfo(
            UnifiedKeyInfo unifiedKeyInfo) {

        if (unifiedKeyInfo == null) {
            return;
        }

        this.unifiedKeyInfo = unifiedKeyInfo;

        final String fingerprint =
                KeyFormattingUtils.convertFingerprintToHex(
                        unifiedKeyInfo.fingerprint()
                );

        mFingerprintView.setText(
                KeyFormattingUtils.formatFingerprint(fingerprint)
        );
    }

    private void onLoadQrCode(Bitmap qrCode) {
        if (qrCode == null) {
            /*
             * A public key can be too large to fit into a single QR code.
             * Since multi-QR is intentionally not supported, hide the QR.
             */
            if (isAdded()) {
                mQrCodeLayout.setVisibility(View.GONE);

                Toast.makeText(
                        requireContext(),
                        "Public key is too large for a single QR code",
                        Toast.LENGTH_LONG
                ).show();
            }

            return;
        }

        if (mQrCodeBitmapCache != null) {
            return;
        }

        mQrCodeBitmapCache = qrCode;

        if (isAdded()) {
            mQrCode.requestLayout();

            AlphaAnimation anim =
                    new AlphaAnimation(0.0f, 1.0f);

            anim.setDuration(200);

            mQrCode.startAnimation(anim);
        }
    }

}
