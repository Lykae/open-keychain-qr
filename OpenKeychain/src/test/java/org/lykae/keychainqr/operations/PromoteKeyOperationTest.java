/*
 * Copyright (C) 2014 Vincent Breitmoser <v.breitmoser@mugenguild.com>
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


import java.io.PrintStream;
import java.security.Security;
import java.util.Arrays;
import java.util.Iterator;

import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLog;
import org.lykae.keychainqr.KeychainTestRunner;
import org.lykae.keychainqr.model.UnifiedKeyInfo;
import org.lykae.keychainqr.operations.results.PgpEditKeyResult;
import org.lykae.keychainqr.operations.results.PromoteKeyResult;
import org.lykae.keychainqr.pgp.CanonicalizedSecretKey;
import org.lykae.keychainqr.pgp.CanonicalizedSecretKey.SecretKeyType;
import org.lykae.keychainqr.pgp.CanonicalizedSecretKeyRing;
import org.lykae.keychainqr.pgp.PgpKeyOperation;
import org.lykae.keychainqr.pgp.UncachedKeyRing;
import org.lykae.keychainqr.pgp.UncachedPublicKey;
import org.lykae.keychainqr.daos.KeyWritableRepository;
import org.lykae.keychainqr.service.ChangeUnlockParcel;
import org.lykae.keychainqr.service.PromoteKeyringParcel;
import org.lykae.keychainqr.service.SaveKeyringParcel;
import org.lykae.keychainqr.service.SaveKeyringParcel.Algorithm;
import org.lykae.keychainqr.service.SaveKeyringParcel.SubkeyAdd;
import org.lykae.keychainqr.support.KeyringTestingHelper;
import org.lykae.keychainqr.util.Passphrase;
import org.lykae.keychainqr.util.TestingUtils;

@RunWith(KeychainTestRunner.class)
public class PromoteKeyOperationTest {

    static UncachedKeyRing mStaticRing;
    static Passphrase mKeyPhrase1 = TestingUtils.testPassphrase1;

    static PrintStream oldShadowStream;

    @BeforeClass
    public static void setUpOnce() throws Exception {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        oldShadowStream = ShadowLog.stream;
        // ShadowLog.stream = System.out;

        PgpKeyOperation op = new PgpKeyOperation(null);

        {
            SaveKeyringParcel.Builder builder = SaveKeyringParcel.buildNewKeyringParcel();
            builder.addSubkeyAdd(SubkeyAdd.createSubkeyAdd(
                    Algorithm.ECDSA, 0, SaveKeyringParcel.Curve.NIST_P256, KeyFlags.CERTIFY_OTHER, 0L));
            builder.addSubkeyAdd(SubkeyAdd.createSubkeyAdd(
                    Algorithm.ECDSA, 0, SaveKeyringParcel.Curve.NIST_P256, KeyFlags.SIGN_DATA, 0L));
            builder.addSubkeyAdd(SubkeyAdd.createSubkeyAdd(
                    Algorithm.ECDH, 0, SaveKeyringParcel.Curve.NIST_P256, KeyFlags.ENCRYPT_COMMS, 0L));
            builder.addUserId("derp");
            builder.setNewUnlock(ChangeUnlockParcel.createUnLockParcelForNewKey(mKeyPhrase1));

            PgpEditKeyResult result = op.createSecretKeyRing(builder.build());
            Assert.assertTrue("initial test key creation must succeed", result.success());
            Assert.assertNotNull("initial test key creation must succeed", result.getRing());

            mStaticRing = result.getRing();
        }

    }

    @Before
    public void setUp() throws Exception {
        KeyWritableRepository databaseInteractor =
                KeyWritableRepository.create(RuntimeEnvironment.getApplication());

        // don't log verbosely here, we're not here to test imports
        ShadowLog.stream = oldShadowStream;

        databaseInteractor.savePublicKeyRing(mStaticRing.extractPublicKeyRing(), null);

        // ok NOW log verbosely!
        ShadowLog.stream = System.out;
    }

    @Test
    public void testPromote() throws Exception {
        KeyWritableRepository keyRepository = KeyWritableRepository.create(RuntimeEnvironment.getApplication());
        PromoteKeyOperation op = new PromoteKeyOperation(RuntimeEnvironment.getApplication(),
                keyRepository, null, null);

        PromoteKeyResult result = op.execute(
                PromoteKeyringParcel.createPromoteKeyringParcel(mStaticRing.getMasterKeyId(), null, null), null);

        Assert.assertTrue("promotion must succeed", result.success());

        {
            UnifiedKeyInfo unifiedKeyInfo = keyRepository.getUnifiedKeyInfo(mStaticRing.getMasterKeyId());
            Assert.assertTrue("key must have a secret now", unifiedKeyInfo.has_any_secret());

            Iterator<UncachedPublicKey> it = mStaticRing.getPublicKeys();
            while (it.hasNext()) {
                long keyId = it.next().getKeyId();
                Assert.assertEquals("all subkeys must be gnu dummy",
                        SecretKeyType.GNU_DUMMY, keyRepository.getSecretKeyType(keyId));
            }
        }

    }

    @Test
    public void testPromoteDivert() throws Exception {
        PromoteKeyOperation op = new PromoteKeyOperation(RuntimeEnvironment.getApplication(),
                KeyWritableRepository.create(RuntimeEnvironment.getApplication()), null, null);

        byte[] aid = Hex.decode("D2760001240102000000012345670000");

        PromoteKeyResult result = op.execute(
                PromoteKeyringParcel.createPromoteKeyringParcel(mStaticRing.getMasterKeyId(), aid, null), null);

        Assert.assertTrue("promotion must succeed", result.success());

        {
            CanonicalizedSecretKeyRing ring = KeyWritableRepository.create(RuntimeEnvironment.getApplication())
                    .getCanonicalizedSecretKeyRing(mStaticRing.getMasterKeyId());

            for (CanonicalizedSecretKey key : ring.secretKeyIterator()) {
                Assert.assertEquals("all subkeys must be divert-to-card",
                        SecretKeyType.DIVERT_TO_CARD, key.getSecretKeyTypeSuperExpensive());
                Assert.assertArrayEquals("all subkeys must have correct iv",
                        aid, key.getIv());
            }

        }
    }

    @Test
    public void testPromoteDivertSpecific() throws Exception {
        PromoteKeyOperation op = new PromoteKeyOperation(RuntimeEnvironment.getApplication(),
                KeyWritableRepository.create(RuntimeEnvironment.getApplication()), null, null);

        byte[] aid = Hex.decode("D2760001240102000000012345670000");

        // only promote the first, rest stays dummy
        long keyId = KeyringTestingHelper.getSubkeyId(mStaticRing, 1);

        PromoteKeyResult result = op.execute(
                PromoteKeyringParcel.createPromoteKeyringParcel(mStaticRing.getMasterKeyId(), aid,
                        Arrays.asList(mStaticRing.getPublicKey(keyId).getFingerprint())), null);

        Assert.assertTrue("promotion must succeed", result.success());

        {
            CanonicalizedSecretKeyRing ring = KeyWritableRepository.create(RuntimeEnvironment.getApplication())
                    .getCanonicalizedSecretKeyRing(mStaticRing.getMasterKeyId());

            for (CanonicalizedSecretKey key : ring.secretKeyIterator()) {
                if (key.getKeyId() == keyId) {
                    Assert.assertEquals("subkey must be divert-to-card",
                            SecretKeyType.DIVERT_TO_CARD, key.getSecretKeyTypeSuperExpensive());
                    Assert.assertArrayEquals("subkey must have correct iv",
                            aid, key.getIv());
                } else {
                    Assert.assertEquals("some subkeys must be gnu dummy",
                            SecretKeyType.GNU_DUMMY, key.getSecretKeyTypeSuperExpensive());
                }
            }

        }
    }

}
