package org.lykae.keychainqr.livedata;


import android.content.Context;

import org.lykae.keychainqr.operations.results.PgpEditKeyResult;
import org.lykae.keychainqr.pgp.PgpKeyOperation;
import org.lykae.keychainqr.service.SaveKeyringParcel;
import org.lykae.keychainqr.ui.keyview.loader.AsyncTaskLiveData;
import org.lykae.keychainqr.util.ProgressScaler;


public class PgpKeyGenerationLiveData extends AsyncTaskLiveData<PgpEditKeyResult> {
    private SaveKeyringParcel saveKeyringParcel;

    public PgpKeyGenerationLiveData(Context context) {
        super(context, null);
    }

    public void setSaveKeyringParcel(SaveKeyringParcel saveKeyringParcel) {
        if (this.saveKeyringParcel == saveKeyringParcel) {
            return;
        }
        this.saveKeyringParcel = saveKeyringParcel;

        updateDataInBackground();
    }

    @Override
    protected PgpEditKeyResult asyncLoadData() {
        if (saveKeyringParcel == null) {
            return null;
        }

        PgpKeyOperation keyOperations = new PgpKeyOperation(new ProgressScaler());
        return keyOperations.createSecretKeyRing(saveKeyringParcel);
    }
}
