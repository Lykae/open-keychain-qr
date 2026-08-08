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

package org.lykae.keychainqr.service;


import java.util.concurrent.atomic.AtomicBoolean;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Parcelable;

import androidx.core.os.CancellationSignal;
import org.lykae.keychainqr.daos.KeyWritableRepository;
import org.lykae.keychainqr.operations.BackupOperation;
import org.lykae.keychainqr.operations.BaseOperation;
import org.lykae.keychainqr.operations.BenchmarkOperation;
import org.lykae.keychainqr.operations.CertifyOperation;
import org.lykae.keychainqr.operations.ChangeUnlockOperation;
import org.lykae.keychainqr.operations.DeleteOperation;
import org.lykae.keychainqr.operations.EditKeyOperation;
import org.lykae.keychainqr.operations.ImportOperation;
import org.lykae.keychainqr.operations.InputDataOperation;
import org.lykae.keychainqr.operations.KeySyncOperation;
import org.lykae.keychainqr.operations.KeySyncParcel;
import org.lykae.keychainqr.operations.PromoteKeyOperation;
import org.lykae.keychainqr.operations.RevokeOperation;
import org.lykae.keychainqr.operations.SignEncryptOperation;
import org.lykae.keychainqr.operations.UploadOperation;
import org.lykae.keychainqr.operations.results.OperationResult;
import org.lykae.keychainqr.pgp.PgpDecryptVerifyInputParcel;
import org.lykae.keychainqr.pgp.PgpDecryptVerifyOperation;
import org.lykae.keychainqr.pgp.Progressable;
import org.lykae.keychainqr.pgp.SignEncryptParcel;
import org.lykae.keychainqr.service.input.CryptoInputParcel;


public class KeychainServiceTask {
    public static KeychainServiceTask create(Activity activity) {
        Context context = activity.getApplicationContext();
        KeyWritableRepository keyRepository = KeyWritableRepository.create(context);
        return new KeychainServiceTask(context, keyRepository);
    }

    private KeychainServiceTask(Context context, KeyWritableRepository keyRepository) {
        this.context = context;
        this.keyRepository = keyRepository;
    }

    private final Context context;
    private final KeyWritableRepository keyRepository;

    @SuppressLint("StaticFieldLeak")
    public CancellationSignal startOperationInBackground(
            Parcelable inputParcel, CryptoInputParcel cryptoInput, OperationCallback operationCallback) {
        AtomicBoolean operationCancelledBoolean = new AtomicBoolean(false);

        AsyncTask<Void, ProgressUpdate, OperationResult> asyncTask =
                new AsyncTask<Void, ProgressUpdate, OperationResult>() {
                    @Override
                    protected OperationResult doInBackground(Void... voids) {
                        BaseOperation op;

                        if (inputParcel instanceof SignEncryptParcel) {
                            op = new SignEncryptOperation(context, keyRepository, asyncProgressable,
                                    operationCancelledBoolean);
                        } else if (inputParcel instanceof PgpDecryptVerifyInputParcel) {
                            op = new PgpDecryptVerifyOperation(context, keyRepository, asyncProgressable);
                        } else if (inputParcel instanceof SaveKeyringParcel) {
                            op = new EditKeyOperation(context, keyRepository, asyncProgressable,
                                    operationCancelledBoolean);
                        } else if (inputParcel instanceof ChangeUnlockParcel) {
                            op = new ChangeUnlockOperation(context, keyRepository, asyncProgressable);
                        } else if (inputParcel instanceof RevokeKeyringParcel) {
                            op = new RevokeOperation(context, keyRepository, asyncProgressable);
                        } else if (inputParcel instanceof CertifyActionsParcel) {
                            op = new CertifyOperation(context, keyRepository, asyncProgressable,
                                    operationCancelledBoolean);
                        } else if (inputParcel instanceof DeleteKeyringParcel) {
                            op = new DeleteOperation(context, keyRepository, asyncProgressable);
                        } else if (inputParcel instanceof PromoteKeyringParcel) {
                            op = new PromoteKeyOperation(context, keyRepository, asyncProgressable,
                                    operationCancelledBoolean);
                        } else if (inputParcel instanceof ImportKeyringParcel) {
                            op = new ImportOperation(context, keyRepository, asyncProgressable,
                                    operationCancelledBoolean);
                        } else if (inputParcel instanceof BackupKeyringParcel) {
                            op = new BackupOperation(context, keyRepository, asyncProgressable,
                                    operationCancelledBoolean);
                        } else if (inputParcel instanceof UploadKeyringParcel) {
                            op = new UploadOperation(context, keyRepository, asyncProgressable,
                                    operationCancelledBoolean);
                        } else if (inputParcel instanceof InputDataParcel) {
                            op = new InputDataOperation(context, keyRepository, asyncProgressable);
                        } else if (inputParcel instanceof BenchmarkInputParcel) {
                            op = new BenchmarkOperation(context, keyRepository, asyncProgressable);
                        } else if (inputParcel instanceof KeySyncParcel) {
                            op = new KeySyncOperation(context, keyRepository, asyncProgressable,
                                    operationCancelledBoolean);
                        } else {
                            throw new AssertionError("Unrecognized input parcel in KeychainService!");
                        }

                        if (isCancelled()) {
                            return null;
                        }

                        // noinspection unchecked, we make sure it's the correct op above
                        return op.execute(inputParcel, cryptoInput);
                    }

                    Progressable asyncProgressable = new Progressable() {
                        @Override
                        public void setPreventCancel() {
                            publishProgress((ProgressUpdate) null);
                        }

                        @Override
                        public void setProgress(Integer resourceId, int current, int total) {
                            publishProgress(new ProgressUpdate(resourceId, current, total));
                        }
                    };

                    @Override
                    protected void onProgressUpdate(ProgressUpdate... values) {
                        ProgressUpdate progressUpdate = values[0];
                        if (progressUpdate == null) {
                            operationCallback.setPreventCancel();
                        } else {
                            operationCallback.setProgress(progressUpdate.resourceId, progressUpdate.current,
                                    progressUpdate.total);
                        }
                    }

                    @Override
                    protected void onPostExecute(OperationResult result) {
                        operationCallback.operationFinished(result);
                    }
                };
        asyncTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);

        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.setOnCancelListener(() -> {
            operationCancelledBoolean.set(true);
        });
        return cancellationSignal;
    }

    public interface OperationCallback {
        void setProgress(Integer message, int current, int total);
        void setPreventCancel();
        void operationFinished(OperationResult data);
    }

    private static class ProgressUpdate {
        public final Integer resourceId;
        public final int current;
        public final int total;

        ProgressUpdate(Integer resourceId, int current, int total) {
            this.resourceId = resourceId;
            this.current = current;
            this.total = total;
        }
    }

}
