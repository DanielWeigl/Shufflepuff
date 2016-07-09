package com.shuffle.moderator;

import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.chan.Chan;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 *
 * Created by Daniel Krawisz on 7/9/16.
 */
public class Mix<Message extends Serializable> {
    public final String id;
    public final int minPlayers;
    public final int maxPlayers;
    public final int maxRetries;
    private final Map<VerificationKey, Chan<Message>> registered = new HashMap<>();
    public final long amount;
    public final long begin;   // The time that the protocol will begin.
    public final long expiration; // The maximum time that the protocol can begin.
    public final long timeout; // The amount of time to wait before a player has assumed to have timedout.

    public Mix(String id, int minPlayers, int maxPlayers, int maxRetries,
               long amount, long begin, long expiration, long timeout) {

        this.maxRetries = maxRetries;
        this.expiration = expiration;

        if (minPlayers < 2)
            throw new IllegalArgumentException("Minimum players must be at least 2.");
        if (maxPlayers < 2)
            throw new IllegalArgumentException("Maximum players must be at least 2.");
        if (maxPlayers < minPlayers)
            throw new IllegalArgumentException("Maximum players must be >= minimum players.");

        this.id = id;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.amount = amount;
        this.begin = begin;
        this.timeout = timeout;
    }

    public synchronized void put(VerificationKey key, Chan<Message> chan) {
        if (registered.size() >= maxPlayers)
            throw new IllegalArgumentException("No more room.");

        if (registered.containsKey(key)) throw new IllegalArgumentException("Already registered.");

        registered.put(key, chan);
    }

    public synchronized void replace(VerificationKey key, Chan<Message> chan) {
        if (!registered.containsKey(key)) throw new IllegalArgumentException("Not registered for this mix.");

        registered.put(key, chan);
    }

    public synchronized boolean ready() {
        if (registered.size() < minPlayers) return false;

        int open = 0;
        for (Chan<Message> chan : registered.values()) {
            if (!chan.closed()) open++;
        }

        return open >= minPlayers;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public void run(Executor exec, final Chan<String> report) {
        exec.execute(new Runnable() {
            @Override
            public void run() {
                //try {
                    // Create initial start message.

                    for (int trial = 1; trial <= maxRetries; trial++) {

                        // Put all channels in an inbox.

                        // tell all the peers to begin.

                        // Receive reports from peers.

                        // The protocol is considered successuful if we receive
                        // only responses which say it was successful. This includes
                        // the case in which no one responds.

                        // If not, first eliminate all nonresponsive players and
                        // all those who claim it was successful.

                        // If there are still issues to resolve, determine if any
                        // players need to receive evidence from any of the others.

                        // If we are not going to restart the protocol, send end message.

                        // Otherwise, construct restart messages.
                    }

                    // notify that the protocol did not complete.
                /*} catch (InterruptedException e) {
                    // Notify everyone that we are shutting down
                    // And report back that we don't know what happened.
                }*/
            }
        });
    }
}
