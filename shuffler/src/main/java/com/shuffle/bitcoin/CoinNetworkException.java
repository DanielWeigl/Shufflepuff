/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.bitcoin;

/**
 * Created by Daniel Krawisz on 12/6/15.
 */
public class CoinNetworkException extends Exception {
    public final Transaction t;
    public final String message;

    public CoinNetworkException(Transaction t) {
        this.t = t;
        message = null;
    }

    public CoinNetworkException(String message) {
        this.message = message;
        t = null;
    }

    @Override
    public String getMessage() {
        if (message == null) return "CoinShuffle Coin Network Exception";
        return message;
    }
}
