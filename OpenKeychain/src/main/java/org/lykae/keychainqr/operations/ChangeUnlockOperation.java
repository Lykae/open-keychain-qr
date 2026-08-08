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

package org.lykae.keychainqr.operations;

import android.content.Context;
import androidx.annotation.NonNull;

import org.lykae.keychainqr.R;
import org.lykae.keychainqr.operations.results.EditKeyResult;
import org.lykae.keychainqr.operations.results.OperationResult;
import org.lykae.keychainqr.operations.results.PgpEditKeyResult;
import org.lykae.keychainqr.operations.results.SaveKeyringResult;
import org.lykae.keychainqr.pgp.CanonicalizedSecretKeyRing;
import org.lykae.keychainqr.pgp.PgpKeyOperation;
import org.lykae.keychainqr.pgp.Progressable;
import org.lykae.keychainqr.pgp.UncachedKeyRing;
import org.lykae.keychainqr.daos.KeyWritableRepository;
import org.lykae.keychainqr.service.ChangeUnlockParcel;
import org.lykae.keychainqr.service.input.CryptoInputParcel;
import org.lykae.keychainqr.ui.util.KeyFormattingUtils;
import org.lykae.keychainqr.util.ProgressScaler;


public class ChangeUnlockOperation extends BaseReadWriteOperation<ChangeUnlockParcel> {

    public ChangeUnlockOperation(Context context, KeyWritableRepository databaseInteractor, Progressable progressable) {
        super(context, databaseInteractor, progressable);
    }

    @NonNull
    public OperationResult execute(ChangeUnlockParcel unlockParcel, CryptoInputParcel cryptoInput) {
        OperationResult.OperationLog log = new OperationResult.OperationLog();
        log.add(OperationResult.LogType.MSG_ED, 0);

        if (unlockParcel == null || unlockParcel.getMasterKeyId() == null) {
            log.add(OperationResult.LogType.MSG_ED_ERROR_NO_PARCEL, 1);
            return new EditKeyResult(EditKeyResult.RESULT_ERROR, log, null);
        }

        // Perform actual modification
        PgpEditKeyResult modifyResult;
        {
            PgpKeyOperation keyOperations =
                    new PgpKeyOperation(new ProgressScaler(mProgressable, 0, 70, 100));

            try {
                    log.add(OperationResult.LogType.MSG_ED_FETCHING, 1,
                            KeyFormattingUtils.convertKeyIdToHex(unlockParcel.getMasterKeyId()));

                    CanonicalizedSecretKeyRing secRing =
                            mKeyRepository.getCanonicalizedSecretKeyRing(unlockParcel.getMasterKeyId());
                    modifyResult = keyOperations.modifyKeyRingPassphrase(secRing, cryptoInput, unlockParcel);

                    if (modifyResult.isPending()) {
                        // obtain original passphrase from user
                        log.add(modifyResult, 1);
                        return new EditKeyResult(log, modifyResult);
                    }
            } catch (KeyWritableRepository.NotFoundException e) {
                log.add(OperationResult.LogType.MSG_ED_ERROR_KEY_NOT_FOUND, 2);
                return new EditKeyResult(EditKeyResult.RESULT_ERROR, log, null);
            }
        }

        log.add(modifyResult, 1);

        if (!modifyResult.success()) {
            // error is already logged by modification
            return new EditKeyResult(EditKeyResult.RESULT_ERROR, log, null);
        }

        // Cannot cancel from here on out!
        mProgressable.setPreventCancel();

        // It's a success, so this must be non-null now
        UncachedKeyRing ring = modifyResult.getRing();

        updateProgress(R.string.progress_saving, 80, 100);
        SaveKeyringResult saveResult = mKeyWritableRepository.saveSecretKeyRing(ring);
        log.add(saveResult, 1);

        // If the save operation didn't succeed, exit here
        if (!saveResult.success()) {
            return new EditKeyResult(EditKeyResult.RESULT_ERROR, log, null);
        }

        updateProgress(R.string.progress_done, 100, 100);
        log.add(OperationResult.LogType.MSG_ED_SUCCESS, 0);
        return new EditKeyResult(EditKeyResult.RESULT_OK, log, ring.getMasterKeyId());

    }

}
