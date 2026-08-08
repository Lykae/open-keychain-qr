package org.lykae.keychainqr.daos;


import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.content.Context;

import org.lykae.keychainqr.KeyMetadataQueries;
import org.lykae.keychainqr.Key_metadata;
import org.lykae.keychainqr.KeychainDatabase;


public class KeyMetadataDao extends AbstractDao {
    KeyMetadataQueries queries = getDatabase().getKeyMetadataQueries();

    public static KeyMetadataDao create(Context context) {
        KeychainDatabase database = KeychainDatabase.getInstance(context);
        DatabaseNotifyManager databaseNotifyManager = DatabaseNotifyManager.create(context);

        return new KeyMetadataDao(database, databaseNotifyManager);
    }

    private KeyMetadataDao(KeychainDatabase database, DatabaseNotifyManager databaseNotifyManager) {
        super(database, databaseNotifyManager);
    }

    public Key_metadata getKeyMetadata(long masterKeyId) {
        return queries.selectByMasterKeyId(masterKeyId).executeAsOneOrNull();
    }

    public void resetAllLastUpdatedTimes() {
        queries.deleteAllLastUpdatedTimes();
    }

    public void renewKeyLastUpdatedTime(long masterKeyId, boolean seenOnKeyservers) {
        queries.replaceKeyMetadata(masterKeyId, new Date(), seenOnKeyservers);
        getDatabaseNotifyManager().notifyKeyMetadataChange(masterKeyId);
    }

    public List<byte[]> getFingerprintsForKeysOlderThan(long olderThan, TimeUnit timeUnit) {
        return queries.selectFingerprintsForKeysOlderThan(new Date(timeUnit.toMillis(olderThan)))
                .executeAsList();
    }
}
