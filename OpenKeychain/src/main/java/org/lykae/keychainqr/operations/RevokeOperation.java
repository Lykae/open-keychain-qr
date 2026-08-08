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

import org.lykae.keychainqr.model.UnifiedKeyInfo;
import org.lykae.keychainqr.operations.results.EditKeyResult;
import org.lykae.keychainqr.operations.results.OperationResult;
import org.lykae.keychainqr.operations.results.RevokeResult;
import org.lykae.keychainqr.pgp.Progressable;
import org.lykae.keychainqr.daos.KeyWritableRepository;
import org.lykae.keychainqr.service.RevokeKeyringParcel;
import org.lykae.keychainqr.service.SaveKeyringParcel;
import org.lykae.keychainqr.service.input.CryptoInputParcel;
import org.lykae.keychainqr.ui.util.KeyFormattingUtils;
import timber.log.Timber;


public class RevokeOperation extends BaseReadWriteOperation<RevokeKeyringParcel> {

    public RevokeOperation(Context context, KeyWritableRepository databaseInteractor, Progressable progressable) {
        super(context, databaseInteractor, progressable);
    }

    @NonNull
    @Override
    public OperationResult execute(RevokeKeyringParcel revokeKeyringParcel,
                                   CryptoInputParcel cryptoInputParcel) {

        // we don't cache passphrases during revocation
        cryptoInputParcel = cryptoInputParcel.withNoCachePassphrase();

        long masterKeyId = revokeKeyringParcel.getMasterKeyId();

        OperationResult.OperationLog log = new OperationResult.OperationLog();
        log.add(OperationResult.LogType.MSG_REVOKE, 0,
                KeyFormattingUtils.beautifyKeyId(masterKeyId));

        try {
            UnifiedKeyInfo keyInfo = mKeyRepository.getUnifiedKeyInfo(masterKeyId);
            if (keyInfo == null) {
                log.add(OperationResult.LogType.MSG_REVOKE_ERROR_KEY_FAIL, 1);
                return new RevokeResult(RevokeResult.RESULT_ERROR, log, masterKeyId);
            }

            // check if this is a master secret key we can work with
            switch (mKeyRepository.getSecretKeyType(masterKeyId)) {
                case GNU_DUMMY:
                    log.add(OperationResult.LogType.MSG_EK_ERROR_DUMMY, 1);
                    return new RevokeResult(RevokeResult.RESULT_ERROR, log, masterKeyId);
            }

            SaveKeyringParcel.Builder saveKeyringParcel =
                    SaveKeyringParcel.buildChangeKeyringParcel(masterKeyId, keyInfo.fingerprint());

            // all revoke operations are made atomic as of now
            saveKeyringParcel.setUpdateOptions(revokeKeyringParcel.isShouldUpload(), true,
                    revokeKeyringParcel.getKeyserver());

            saveKeyringParcel.addRevokeSubkey(masterKeyId);

            EditKeyResult revokeAndUploadResult = new EditKeyOperation(mContext,
                    mKeyWritableRepository, mProgressable, mCancelled).execute(
                            saveKeyringParcel.build(), cryptoInputParcel);

            if (revokeAndUploadResult.isPending()) {
                return revokeAndUploadResult;
            }

            log.add(revokeAndUploadResult, 1);

            if (revokeAndUploadResult.success()) {
                log.add(OperationResult.LogType.MSG_REVOKE_OK, 1);
                return new RevokeResult(RevokeResult.RESULT_OK, log, masterKeyId);
            } else {
                log.add(OperationResult.LogType.MSG_REVOKE_ERROR_KEY_FAIL, 1);
                return new RevokeResult(RevokeResult.RESULT_ERROR, log, masterKeyId);
            }

        } catch (KeyWritableRepository.NotFoundException e) {
            Timber.e(e, "could not find key to revoke");
            log.add(OperationResult.LogType.MSG_REVOKE_ERROR_KEY_FAIL, 1);
            return new RevokeResult(RevokeResult.RESULT_ERROR, log, masterKeyId);
        }
    }

}
