/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.bitcoin;

import com.shuffle.p2p.Bytestring;

import java.io.Serializable;

/**
 *
 * Should be comparable according to the lexicographic order of the
 * address corresponding to the keys.
 *
 * Created by Daniel Krawisz on 12/3/15.
 */
public interface VerificationKey extends Comparable, Serializable {

    boolean verify(Bytestring payload, Bytestring signature);

    boolean equals(Object vk);

    // Get the cryptocurrency address corresponding to this public key.
    Address address();
}
