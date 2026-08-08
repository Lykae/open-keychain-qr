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

package org.lykae.keychainqr.ui.keyview;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.lykae.keychainqr.R;
import org.lykae.keychainqr.daos.KeyRepository;
import org.lykae.keychainqr.daos.KeyRepository.NotFoundException;
import org.lykae.keychainqr.model.UnifiedKeyInfo;
import org.lykae.keychainqr.operations.results.EditKeyResult;
import org.lykae.keychainqr.operations.results.OperationResult;
import org.lykae.keychainqr.pgp.CanonicalizedSecretKey.SecretKeyType;
import org.lykae.keychainqr.securitytoken.SecurityTokenConnection;
import org.lykae.keychainqr.service.ChangeUnlockParcel;
import org.lykae.keychainqr.service.PassphraseCacheService;
import org.lykae.keychainqr.service.input.RequiredInputParcel;
import org.lykae.keychainqr.ui.BackupActivity;
import org.lykae.keychainqr.ui.CertifyFingerprintActivity;
import org.lykae.keychainqr.ui.CertifyKeyActivity;
import org.lykae.keychainqr.ui.DeleteKeyDialogActivity;
import org.lykae.keychainqr.ui.EncryptFilesActivity;
import org.lykae.keychainqr.ui.EncryptTextActivity;
import org.lykae.keychainqr.ui.ImportKeysProxyActivity;
import org.lykae.keychainqr.ui.MainActivity;
import org.lykae.keychainqr.ui.PassphraseDialogActivity;
import org.lykae.keychainqr.ui.QrCodeViewActivity;
import org.lykae.keychainqr.ui.ViewKeyAdvActivity;
import org.lykae.keychainqr.ui.base.BaseSecurityTokenActivity;
import org.lykae.keychainqr.ui.base.CryptoOperationHelper;
import org.lykae.keychainqr.ui.dialog.SetPassphraseDialogFragment;
import org.lykae.keychainqr.ui.keyview.UnifiedKeyInfoViewModel;
import org.lykae.keychainqr.ui.util.ContentDescriptionHint;
import org.lykae.keychainqr.ui.util.FormattingUtils;
import org.lykae.keychainqr.ui.util.KeyFormattingUtils;
import org.lykae.keychainqr.ui.util.KeyFormattingUtils.State;
import org.lykae.keychainqr.ui.util.Notify;
import org.lykae.keychainqr.ui.util.QrCodeUtils;
import org.lykae.keychainqr.util.ShareKeyHelper;

import timber.log.Timber;

public class ViewKeyActivity extends BaseSecurityTokenActivity {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            REQUEST_QR_KEY,
            REQUEST_BACKUP,
            REQUEST_CERTIFY,
            REQUEST_DELETE
    })
    private @interface RequestType {
    }

    static final int REQUEST_QR_KEY = 1;
    static final int REQUEST_BACKUP = 2;
    static final int REQUEST_CERTIFY = 3;
    static final int REQUEST_DELETE = 4;

    public static final String EXTRA_MASTER_KEY_ID = "master_key_id";
    public static final String EXTRA_DISPLAY_RESULT = "display_result";

    KeyRepository keyRepository;

    private CryptoOperationHelper<ChangeUnlockParcel, EditKeyResult> editOpHelper;
    private ChangeUnlockParcel changeUnlockParcel;

    private TextView statusText;
    private ImageView statusImage;
    private AppBarLayout appBarLayout;
    private CollapsingToolbarLayout collapsingToolbarLayout;

    private ImageButton actionEncryptFile;
    private ImageButton actionEncryptText;
    private ImageButton actionShare;
    private ImageButton actionShareClipboard;
    private FloatingActionButton floatingActionButton;
    private ImageView qrCodeView;
    private CardView qrCodeLayout;

    /*
     * This now stores the actual armored public key used for the QR,
     * rather than a fingerprint.
     */
    private String qrCodeLoaded;

    private UnifiedKeyInfo unifiedKeyInfo;

    public static Intent getViewKeyActivityIntent(
            @NonNull Context context,
            long masterKeyId) {

        Intent viewIntent =
                new Intent(context, ViewKeyActivity.class);

        viewIntent.putExtra(
                ViewKeyActivity.EXTRA_MASTER_KEY_ID,
                masterKeyId
        );

        return viewIntent;
    }

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(
                    LayoutParams.FLAG_TRANSLUCENT_STATUS
            );
        }

        super.onCreate(savedInstanceState);

        keyRepository = KeyRepository.create(this);

        editOpHelper =
                new CryptoOperationHelper<>(
                        2,
                        this,
                        editKeyCallback,
                        null
                );

        setTitle(null);

        statusText =
                findViewById(R.id.view_key_status);

        statusImage =
                findViewById(R.id.view_key_status_image);

        appBarLayout =
                findViewById(R.id.app_bar_layout);

        collapsingToolbarLayout =
                findViewById(R.id.collapsing_toolbar);

        actionEncryptFile =
                findViewById(
                        R.id.view_key_action_encrypt_files
                );

        actionEncryptText =
                findViewById(
                        R.id.view_key_action_encrypt_text
                );

        actionShare =
                findViewById(
                        R.id.view_key_action_share
                );

        actionShareClipboard =
                findViewById(
                        R.id.view_key_action_share_clipboard
                );

        floatingActionButton =
                findViewById(R.id.fab);

        qrCodeView =
                findViewById(
                        R.id.view_key_qr_code
                );

        qrCodeLayout =
                findViewById(
                        R.id.view_key_qr_code_layout
                );

        ContentDescriptionHint.setup(actionEncryptFile);
        ContentDescriptionHint.setup(actionEncryptText);
        ContentDescriptionHint.setup(actionShare);
        ContentDescriptionHint.setup(actionShareClipboard);
        ContentDescriptionHint.setup(floatingActionButton);

        long masterKeyId;

        Intent intent = getIntent();

        if (intent.hasExtra(EXTRA_MASTER_KEY_ID)) {

            masterKeyId =
                    intent.getLongExtra(
                            EXTRA_MASTER_KEY_ID,
                            0L
                    );

        } else {

            throw new IllegalArgumentException(
                    "Missing required extra master_key_id"
            );
        }

        actionEncryptFile.setOnClickListener(
                v -> encrypt(false)
        );

        actionEncryptText.setOnClickListener(
                v -> encrypt(true)
        );

        actionShare.setOnClickListener(
                v -> ShareKeyHelper.shareKey(
                        this,
                        masterKeyId
                )
        );

        actionShareClipboard.setOnClickListener(
                v -> ShareKeyHelper.shareKeyToClipboard(
                        this,
                        masterKeyId
                )
        );

        /*
         * Scan an actual public key from the QR.
         * The scanner imports it locally rather than
         * returning a fingerprint.
         */
        floatingActionButton.setOnClickListener(
                v -> scanQrCode()
        );

        qrCodeLayout.setOnClickListener(
                v -> showQrCodeDialog()
        );

        UnifiedKeyInfoViewModel viewModel =
                ViewModelProviders
                        .of(this)
                        .get(UnifiedKeyInfoViewModel.class);

        viewModel.setMasterKeyId(masterKeyId);

        viewModel
                .getUnifiedKeyInfoLiveData(
                        getApplicationContext()
                )
                .observe(
                        this,
                        this::onLoadUnifiedKeyInfo
                );

        if (savedInstanceState == null
                && intent.hasExtra(EXTRA_DISPLAY_RESULT)) {

            OperationResult result =
                    intent.getParcelableExtra(
                            EXTRA_DISPLAY_RESULT
                    );

            result.createNotify(this).show();
        }

        if (savedInstanceState != null) {
            return;
        }

        FragmentManager manager =
                getSupportFragmentManager();

        ViewKeyFragment frag =
                ViewKeyFragment.newInstance();

        manager.beginTransaction()
                .replace(
                        R.id.view_key_fragment,
                        frag,
                        "view_key_fragment"
                )
                .commit();
    }

    @Override
    protected void initLayout() {
        setContentView(R.layout.view_key_activity);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(
                R.menu.key_view,
                menu
        );

        /*
         * The old keyserver refresh menu item has been
         * intentionally removed from this activity.
         *
         * If it still exists in key_view.xml, remove:
         *
         * menu_key_view_refresh
         */

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case android.R.id.home: {

                Intent homeIntent =
                        new Intent(
                                this,
                                MainActivity.class
                        );

                homeIntent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                );

                startActivity(homeIntent);

                return true;
            }

            case R.id.menu_key_change_password: {

                changePassword();

                return true;
            }

            case R.id.menu_key_view_backup: {

                startPassphraseActivity(
                        REQUEST_BACKUP
                );

                return true;
            }

            case R.id.menu_key_view_delete: {

                deleteKey();

                return true;
            }

            case R.id.menu_key_view_advanced: {

                Intent advancedIntent =
                        new Intent(
                                this,
                                ViewKeyAdvActivity.class
                        );

                advancedIntent.putExtra(
                        ViewKeyAdvActivity.EXTRA_MASTER_KEY_ID,
                        unifiedKeyInfo.master_key_id()
                );

                startActivity(advancedIntent);

                return true;
            }

            case R.id.menu_key_view_certify_fingerprint: {

                certifyFingerprint();

                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        if (unifiedKeyInfo == null) {
            return false;
        }

        MenuItem backupKey =
                menu.findItem(
                        R.id.menu_key_view_backup
                );

        backupKey.setVisible(
                unifiedKeyInfo.has_any_secret()
        );

        MenuItem changePassword =
                menu.findItem(
                        R.id.menu_key_change_password
                );

        changePassword.setVisible(
                unifiedKeyInfo.has_any_secret()
        );

        MenuItem certifyFingerprint =
                menu.findItem(
                        R.id.menu_key_view_certify_fingerprint
                );

        certifyFingerprint.setVisible(
                !unifiedKeyInfo.has_any_secret()
                        && !unifiedKeyInfo.is_verified()
                        && !unifiedKeyInfo.is_expired()
                        && !unifiedKeyInfo.is_revoked()
        );

        return true;
    }

    private void changePassword() {

        Handler returnHandler =
                new Handler() {

                    @Override
                    public void handleMessage(
                            Message message) {

                        if (message.what ==
                                SetPassphraseDialogFragment.MESSAGE_OKAY) {

                            Bundle data =
                                    message.getData();

                            changeUnlockParcel =
                                    ChangeUnlockParcel
                                            .createChangeUnlockParcel(
                                                    unifiedKeyInfo.master_key_id(),
                                                    unifiedKeyInfo.fingerprint(),
                                                    data.getParcelable(
                                                            SetPassphraseDialogFragment.MESSAGE_NEW_PASSPHRASE
                                                    )
                                            );

                            editOpHelper.cryptoOperation();
                        }
                    }
                };

        Messenger messenger =
                new Messenger(returnHandler);

        SetPassphraseDialogFragment setPassphraseDialog =
                SetPassphraseDialogFragment.newInstance(
                        messenger,
                        R.string.title_change_passphrase
                );

        setPassphraseDialog.show(
                getSupportFragmentManager(),
                "setPassphraseDialog"
        );
    }

    private void displayResult(OperationResult result) {
        result.createNotify(this).show();
    }

    /**
     * Scan an actual ASCII-armored public key.
     *
     * There is no fingerprint comparison and no
     * keyserver lookup in this flow.
     */
    private void scanQrCode() {

        Intent scanQrCode =
                new Intent(
                        this,
                        ImportKeysProxyActivity.class
                );

        scanQrCode.setAction(
                ImportKeysProxyActivity.ACTION_SCAN_IMPORT
        );

        startActivityForResult(
                scanQrCode,
                REQUEST_QR_KEY
        );
    }

    private void certifyFingerprint() {

        Intent intent =
                new Intent(
                        this,
                        CertifyFingerprintActivity.class
                );

        intent.putExtra(
                CertifyFingerprintActivity.EXTRA_MASTER_KEY_ID,
                unifiedKeyInfo.master_key_id()
        );

        startActivityForResult(
                intent,
                REQUEST_CERTIFY
        );
    }

    private void certifyImmediate() {

        Intent intent =
                new Intent(
                        this,
                        CertifyKeyActivity.class
                );

        intent.putExtra(
                CertifyKeyActivity.EXTRA_KEY_IDS,
                new long[]{
                        unifiedKeyInfo.master_key_id()
                }
        );

        startActivityForResult(
                intent,
                REQUEST_CERTIFY
        );
    }

    /**
     * Show the actual ASCII-armored public key in one QR.
     */
    private void showQrCodeDialog() {

        if (unifiedKeyInfo == null) {
            return;
        }

        try {

            String armoredKey =
                    keyRepository
                            .getPublicKeyRingAsArmoredString(
                                    unifiedKeyInfo.master_key_id()
                            );

            if (armoredKey == null
                    || armoredKey.trim().isEmpty()) {

                Notify.create(
                        this,
                        R.string.error_key_not_found,
                        Notify.Style.ERROR
                ).show();

                return;
            }

            Intent qrCodeIntent =
                    new Intent(
                            this,
                            QrCodeViewActivity.class
                    );

            /*
             * Pass the complete public key instead of
             * passing the fingerprint.
             */
            qrCodeIntent.putExtra(
                    QrCodeViewActivity.EXTRA_TEXT,
                    armoredKey
            );

            Bundle opts = null;

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.LOLLIPOP) {

                ActivityOptions options =
                        ActivityOptions
                                .makeSceneTransitionAnimation(
                                        this,
                                        qrCodeLayout,
                                        "qr_code"
                                );

                opts = options.toBundle();
            }

            ActivityCompat.startActivity(
                    this,
                    qrCodeIntent,
                    opts
            );

        } catch (IOException | NotFoundException e) {

            Timber.e(
                    e,
                    "Unable to export public key for QR"
            );

            Notify.create(
                    this,
                    R.string.error_key_processing,
                    Notify.Style.ERROR
            ).show();
        }
    }

    private void startPassphraseActivity(
            int requestCode) {

        if (keyHasPassphrase()) {

            Intent intent =
                    new Intent(
                            this,
                            PassphraseDialogActivity.class
                    );

            long masterKeyId =
                    unifiedKeyInfo.master_key_id();

            RequiredInputParcel requiredInput =
                    RequiredInputParcel
                            .createRequiredDecryptPassphrase(
                                    masterKeyId,
                                    masterKeyId
                            );

            requiredInput.mSkipCaching = true;

            intent.putExtra(
                    PassphraseDialogActivity.EXTRA_REQUIRED_INPUT,
                    requiredInput
            );

            startActivityForResult(
                    intent,
                    requestCode
            );

        } else {

            startBackupActivity();
        }
    }

    private boolean keyHasPassphrase() {

        try {

            long masterKeyId =
                    unifiedKeyInfo.master_key_id();

            SecretKeyType secretKeyType =
                    keyRepository.getSecretKeyType(
                            masterKeyId
                    );

            switch (secretKeyType) {

                case PASSPHRASE_EMPTY:
                case GNU_DUMMY:
                case DIVERT_TO_CARD:
                case UNAVAILABLE:
                    return false;

                default:
                    return true;
            }

        } catch (NotFoundException e) {

            return false;
        }
    }

    private void startBackupActivity() {

        Intent intent =
                new Intent(
                        this,
                        BackupActivity.class
                );

        intent.putExtra(
                BackupActivity.EXTRA_MASTER_KEY_IDS,
                new long[]{
                        unifiedKeyInfo.master_key_id()
                }
        );

        intent.putExtra(
                BackupActivity.EXTRA_SECRET,
                true
        );

        startActivity(intent);
    }

    private void deleteKey() {

        Intent deleteIntent =
                new Intent(
                        this,
                        DeleteKeyDialogActivity.class
                );

        deleteIntent.putExtra(
                DeleteKeyDialogActivity.EXTRA_DELETE_MASTER_KEY_IDS,
                new long[]{
                        unifiedKeyInfo.master_key_id()
                }
        );

        deleteIntent.putExtra(
                DeleteKeyDialogActivity.EXTRA_HAS_SECRET,
                unifiedKeyInfo.has_any_secret()
        );

        /*
         * Deliberately no EXTRA_KEYSERVER.
         *
         * This activity no longer supplies a keyserver
         * for deletion/upload operations.
         */

        startActivityForResult(
                deleteIntent,
                REQUEST_DELETE
        );
    }

    @Override
    protected void onActivityResult(
            @RequestType int requestCode,
            int resultCode,
            Intent data) {

        if (editOpHelper.handleActivityResult(
                requestCode,
                resultCode,
                data)) {

            return;
        }

        if (resultCode != Activity.RESULT_OK) {

            super.onActivityResult(
                    requestCode,
                    resultCode,
                    data
            );

            return;
        }

        switch (requestCode) {

            case REQUEST_QR_KEY: {

                /*
                 * The QR scanner now imports the key itself.
                 *
                 * There is intentionally no fingerprint
                 * comparison here.
                 */
                if (data != null
                        && data.hasExtra(
                        OperationResult.EXTRA_RESULT)) {

                    OperationResult result =
                            data.getParcelableExtra(
                                    OperationResult.EXTRA_RESULT
                            );

                    if (result != null) {
                        result.createNotify(this).show();
                    }

                    return;
                }

                return;
            }

            case REQUEST_BACKUP: {

                startBackupActivity();

                return;
            }

            case REQUEST_DELETE: {

                setResult(
                        RESULT_OK,
                        data
                );

                finish();

                return;
            }

            case REQUEST_CERTIFY: {

                if (data != null
                        && data.hasExtra(
                        OperationResult.EXTRA_RESULT)) {

                    OperationResult result =
                            data.getParcelableExtra(
                                    OperationResult.EXTRA_RESULT
                            );

                    if (result != null) {
                        result.createNotify(this).show();
                    }
                }

                return;
            }
        }

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );
    }

    @Override
    protected void onSecurityTokenPostExecute(
            SecurityTokenConnection stConnection) {

        super.onSecurityTokenPostExecute(
                stConnection
        );

        finish();
    }

    private void encrypt(boolean text) {

        if (!unifiedKeyInfo.has_encrypt_key()) {

            Notify.create(
                    this,
                    R.string.error_no_encrypt_subkey,
                    Notify.Style.ERROR
            ).show();

            return;
        }

        long[] encryptionKeyIds =
                new long[]{
                        unifiedKeyInfo.master_key_id()
                };

        Intent intent;

        if (text) {

            intent =
                    new Intent(
                            this,
                            EncryptTextActivity.class
                    );

            intent.setAction(
                    EncryptTextActivity.ACTION_ENCRYPT_TEXT
            );

            intent.putExtra(
                    EncryptTextActivity.EXTRA_ENCRYPTION_KEY_IDS,
                    encryptionKeyIds
            );

        } else {

            intent =
                    new Intent(
                            this,
                            EncryptFilesActivity.class
                    );

            intent.setAction(
                    EncryptFilesActivity.ACTION_ENCRYPT_DATA
            );

            intent.putExtra(
                    EncryptFilesActivity.EXTRA_ENCRYPTION_KEY_IDS,
                    encryptionKeyIds
            );
        }

        startActivityForResult(
                intent,
                0
        );
    }

    private void onLoadUnifiedKeyInfo(
            UnifiedKeyInfo unifiedKeyInfo) {

        if (unifiedKeyInfo == null) {
            return;
        }

        this.unifiedKeyInfo = unifiedKeyInfo;

        String name =
                unifiedKeyInfo.name();

        boolean isAnonymousKey =
                name == null
                        && unifiedKeyInfo.email() == null;

        if (isAnonymousKey) {

            String readableKeyId =
                    KeyFormattingUtils.beautifyKeyId(
                            unifiedKeyInfo.master_key_id()
                    );

            collapsingToolbarLayout.setTitle(
                    readableKeyId
            );

        } else {

            collapsingToolbarLayout.setTitle(
                    name != null
                            ? name
                            : getString(
                            R.string.user_id_no_name
                    )
            );
        }

        boolean showStatusText =
                unifiedKeyInfo.is_secure()
                        && !unifiedKeyInfo.is_expired()
                        && !unifiedKeyInfo.is_revoked();

        if (showStatusText) {

            statusText.setVisibility(
                    View.VISIBLE
            );

            if (unifiedKeyInfo.has_any_secret()) {

                statusText.setText(
                        R.string.view_key_my_key
                );

            } else if (unifiedKeyInfo.is_verified()) {

                statusText.setText(
                        R.string.view_key_verified
                );

            } else {

                statusText.setText(
                        R.string.view_key_unverified
                );
            }

        } else {

            statusText.setVisibility(
                    View.GONE
            );
        }

        int color;

        if (unifiedKeyInfo.is_revoked()) {

            statusImage.setVisibility(
                    View.VISIBLE
            );

            KeyFormattingUtils.setStatusImage(
                    this,
                    statusImage,
                    statusText,
                    State.REVOKED,
                    R.color.icons,
                    true
            );

            color =
                    ContextCompat.getColor(
                            this,
                            R.color.key_flag_red
                    );

            actionEncryptFile.setVisibility(
                    View.INVISIBLE
            );

            actionEncryptText.setVisibility(
                    View.INVISIBLE
            );

            hideFab();

            qrCodeLayout.setVisibility(
                    View.GONE
            );

        } else if (unifiedKeyInfo.is_expired()) {

            statusImage.setVisibility(
                    View.VISIBLE
            );

            KeyFormattingUtils.setStatusImage(
                    this,
                    statusImage,
                    statusText,
                    State.EXPIRED,
                    R.color.icons,
                    true
            );

            color =
                    ContextCompat.getColor(
                            this,
                            R.color.key_flag_red
                    );

            actionEncryptFile.setVisibility(
                    View.INVISIBLE
            );

            actionEncryptText.setVisibility(
                    View.INVISIBLE
            );

            hideFab();

            qrCodeLayout.setVisibility(
                    View.GONE
            );

        } else if (!unifiedKeyInfo.is_secure()) {

            statusImage.setVisibility(
                    View.VISIBLE
            );

            KeyFormattingUtils.setStatusImage(
                    this,
                    statusImage,
                    statusText,
                    State.INSECURE,
                    R.color.icons,
                    true
            );

            color =
                    ContextCompat.getColor(
                            this,
                            R.color.key_flag_red
                    );

            actionEncryptFile.setVisibility(
                    View.INVISIBLE
            );

            actionEncryptText.setVisibility(
                    View.INVISIBLE
            );

            hideFab();

            qrCodeLayout.setVisibility(
                    View.GONE
            );

        } else if (unifiedKeyInfo.has_any_secret()) {

            statusImage.setVisibility(
                    View.GONE
            );

            color =
                    ContextCompat.getColor(
                            this,
                            R.color.key_flag_green
                    );

            /*
             * QR is now generated from the complete local
             * ASCII-armored public key.
             */
            if (qrCodeLoaded == null) {
                loadQrCode(
                        unifiedKeyInfo.master_key_id()
                );
            }

            qrCodeLayout.setVisibility(
                    View.VISIBLE
            );

            RelativeLayout.LayoutParams statusParams =
                    (RelativeLayout.LayoutParams)
                            statusText.getLayoutParams();

            statusParams.setMargins(
                    FormattingUtils.dpToPx(
                            this,
                            48
                    ),
                    0,
                    0,
                    0
            );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.JELLY_BEAN_MR1) {

                statusParams.setMarginEnd(0);
            }

            statusParams.addRule(
                    RelativeLayout.LEFT_OF,
                    R.id.view_key_qr_code_layout
            );

            statusText.setLayoutParams(
                    statusParams
            );

            actionEncryptFile.setVisibility(
                    View.VISIBLE
            );

            actionEncryptText.setVisibility(
                    View.VISIBLE
            );

            actionShare.setVisibility(
                    View.VISIBLE
            );

            actionShareClipboard.setVisibility(
                    View.VISIBLE
            );

            hideFab();

        } else {

            actionEncryptFile.setVisibility(
                    View.VISIBLE
            );

            actionEncryptText.setVisibility(
                    View.VISIBLE
            );

            actionShare.setVisibility(
                    View.VISIBLE
            );

            actionShareClipboard.setVisibility(
                    View.VISIBLE
            );

            qrCodeLayout.setVisibility(
                    View.GONE
            );

            if (unifiedKeyInfo.is_verified()) {

                statusText.setText(
                        R.string.view_key_verified
                );

                statusImage.setVisibility(
                        View.VISIBLE
                );

                KeyFormattingUtils.setStatusImage(
                        this,
                        statusImage,
                        statusText,
                        State.VERIFIED,
                        R.color.icons,
                        true
                );

                color =
                        ContextCompat.getColor(
                                this,
                                R.color.key_flag_green
                        );

                hideFab();

            } else {

                statusText.setText(
                        R.string.view_key_unverified
                );

                statusImage.setVisibility(
                        View.VISIBLE
                );

                KeyFormattingUtils.setStatusImage(
                        this,
                        statusImage,
                        statusText,
                        State.UNVERIFIED,
                        R.color.icons,
                        true
                );

                color =
                        ContextCompat.getColor(
                                this,
                                R.color.key_flag_orange
                        );

                showFab();
            }
        }

        statusImage.setAlpha(80);
    }

    /**
     * Generate a single QR containing the complete
     * ASCII-armored public key.
     */
    private void loadQrCode(final long masterKeyId) {

        AsyncTask<Void, Void, String> loadTask =
                new AsyncTask<Void, Void, String>() {

                    private Exception error;

                    @Override
                    protected String doInBackground(
                            Void... unused) {

                        try {

                            return keyRepository
                                    .getPublicKeyRingAsArmoredString(
                                            masterKeyId
                                    );

                        } catch (
                                IOException
                                | NotFoundException e) {

                            error = e;
                            return null;
                        }
                    }

                    @Override
                    protected void onPostExecute(
                            String armoredKey) {

                        if (armoredKey == null
                                || armoredKey.trim().isEmpty()) {

                            Timber.e(
                                    error,
                                    "Unable to obtain public key"
                            );

                            Notify.create(
                                    ViewKeyActivity.this,
                                    R.string.error_key_processing,
                                    Notify.Style.ERROR
                            ).show();

                            return;
                        }

                        try {

                            /*
                             * QrCodeUtils receives the actual
                             * ASCII-armored public key.
                             */
                            Bitmap qrCode =
                                    QrCodeUtils.getQRCodeBitmap(
                                            armoredKey,
                                            0
                                    );

                            qrCodeLoaded =
                                    armoredKey;

                            int size =
                                    ViewKeyActivity.this
                                            .qrCodeView
                                            .getHeight();

                            if (size <= 0) {
                                size =
                                        ViewKeyActivity.this
                                                .qrCodeView
                                                .getWidth();
                            }

                            Bitmap scaled =
                                    Bitmap.createScaledBitmap(
                                            qrCode,
                                            size,
                                            size,
                                            false
                                    );

                            ViewKeyActivity.this
                                    .qrCodeView
                                    .setImageBitmap(
                                            scaled
                                    );

                            AlphaAnimation anim =
                                    new AlphaAnimation(
                                            0.0f,
                                            1.0f
                                    );

                            anim.setDuration(200);

                            ViewKeyActivity.this
                                    .qrCodeView
                                    .startAnimation(anim);

                        } catch (Exception e) {

                            Timber.e(
                                    e,
                                    "Public key is too large for one QR code"
                            );

                            Notify.create(
                                    ViewKeyActivity.this,
                                    "Public key is too large for one QR code",
                                    Notify.Style.ERROR
                            ).show();
                        }
                    }
                };

        loadTask.execute();
    }

    /**
     * Helper to show Fab.
     */
    private void showFab() {

        CoordinatorLayout.LayoutParams p =
                (CoordinatorLayout.LayoutParams)
                        floatingActionButton
                                .getLayoutParams();

        p.setBehavior(
                new FloatingActionButton.Behavior()
        );

        p.setAnchorId(
                R.id.app_bar_layout
        );

        floatingActionButton.setLayoutParams(p);

        floatingActionButton.setVisibility(
                View.VISIBLE
        );
    }

    /**
     * Helper to hide Fab.
     */
    private void hideFab() {

        CoordinatorLayout.LayoutParams p =
                (CoordinatorLayout.LayoutParams)
                        floatingActionButton
                                .getLayoutParams();

        p.setBehavior(null);
        p.setAnchorId(View.NO_ID);

        floatingActionButton.setLayoutParams(p);

        floatingActionButton.setVisibility(
                View.GONE
        );
    }

    CryptoOperationHelper.Callback<
            ChangeUnlockParcel,
            EditKeyResult> editKeyCallback =
            new CryptoOperationHelper.Callback<
                    ChangeUnlockParcel,
                    EditKeyResult>() {

                @Override
                public ChangeUnlockParcel createOperationInput() {
                    return changeUnlockParcel;
                }

                @Override
                public void onCryptoOperationSuccess(
                        EditKeyResult result) {

                    displayResult(result);

                    long masterKeyId =
                            unifiedKeyInfo.master_key_id();

                    PassphraseCacheService
                            .clearCachedPassphrase(
                                    getApplicationContext(),
                                    masterKeyId,
                                    masterKeyId
                            );
                }

                @Override
                public void onCryptoOperationCancelled() {
                }

                @Override
                public void onCryptoOperationError(
                        EditKeyResult result) {

                    displayResult(result);
                }

                @Override
                public boolean onCryptoSetProgress(
                        String msg,
                        int progress,
                        int max) {

                    return true;
                }
            };
}