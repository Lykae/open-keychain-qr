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


import androidx.fragment.app.Fragment;

import org.lykae.keychainqr.util.Passphrase;


public abstract class EncryptModeFragment extends Fragment {

    public abstract boolean isAsymmetric();

    public abstract long getAsymmetricSigningKeyId();
    public abstract long[] getAsymmetricEncryptionKeyIds();
    public abstract String[] getAsymmetricEncryptionUserIds();

    public abstract Passphrase getSymmetricPassphrase();

}
