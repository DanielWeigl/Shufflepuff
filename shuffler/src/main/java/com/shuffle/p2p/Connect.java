/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.p2p;

import com.shuffle.bitcoin.Crypto;
import com.shuffle.chan.Inbox;
import com.shuffle.chan.Send;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A class for setting up Network objects. It manages setting up all all the necessary
 * connections between peers.
 *
 * Created by Daniel Krawisz on 2/16/16.
 */
public class Connect<Identity, P extends Serializable> implements Connection<Identity> {

    // The list of peers will be altered by two threads; the one for initiating connections
    // and the one for receiving connections. We set it in its own class to allow for some
    // synchronized functions.
    private class Peers {

        // The list of peers which we have not connected with yet.
        private final Queue<Identity> unconnected = new LinkedList<>();
        private final Set<Identity> remaining = new TreeSet<>();

        private final Collector<Identity, P> collector;

        private Peers(Collector<Identity, P> collector) {
            this.collector = collector;
        }

        public void queue(Identity identity) {
            if (identity == null) {
                throw new NullPointerException();
            }

            if (remaining.contains(identity)) return;

            unconnected.add(identity);
            remaining.add(identity);
        }

        public Identity peek() {
            return unconnected.peek();
        }

        boolean rotate() {
            Identity identity = unconnected.poll();
            if (identity == null) {
                return false;
            }

            unconnected.add(identity);
            return true;
        }

        private boolean connect(Session<Identity, P> session) throws InterruptedException {
            Identity addr = session.peer().identity();

            return remaining.remove(addr) && collector.put(session);

        }

        public synchronized boolean openSession(
                Identity identity,
                Peer<Identity, P> peer) throws InterruptedException, IOException {

            Send<P> processor = collector.inbox.receivesFrom(identity);
            if (processor != null) {
                Session<Identity, P> session =
                        peer.openSession(processor);

                if (session != null) {
                    remove();
                    connect(session);
                    return true;
                } else {
                    processor.close();
                    return false;
                }
            }
            return false;
        }

        boolean connected(Identity identity) {
            return collector.connected.containsKey(identity);
        }

        // Removes the first element from the list.
        void remove() {
            unconnected.remove();
        }

        @Override
        public String toString() {
            return unconnected.toString();
        }
    }

    // Keep track of the number of connection attempt retries for each address.
    private class Retries {
        final Map<Identity, Integer> retries = new HashMap<>();

        public int retries(Identity identity) {
            Integer r = retries.get(identity);
            if (r == null) {
                retries.put(identity, 0);
                return 0;
            }

            return r;
        }

        public int increment(Identity identity) {
            Integer r = retries.get(identity);

            if (r == null) {
                r = 0;
            }
            r++;

            retries.put(identity, r);
            return r;
        }
    }

    private final Channel<Identity, P> channel;
    private final Connection<Identity> connection;
    private final Collector<Identity, P> collector;
    private final Crypto crypto;

    private boolean finished = false;

    public Connect(Channel<Identity, P> channel, Crypto crypto)
            throws InterruptedException, IOException {

        this(channel, crypto, 100);
    }

    public Connect(Channel<Identity, P> channel, Crypto crypto, int capacity)
            throws InterruptedException, IOException {

        if (channel == null || crypto == null) throw new NullPointerException();

        collector = new Collector<>(new Inbox<Identity, P>(capacity));

        connection = channel.open(collector);
        if (connection == null ) throw new IllegalArgumentException();

        this.channel = channel;
        this.crypto = crypto;
    }

    // Connect to all peers; remote peers can be initiating connections to us as well.
    public Collector<Identity, P> connect(
            SortedSet<Identity> addrs,
            int maxRetries) throws IOException, InterruptedException {

        if (addrs == null) throw new NullPointerException();

        if (finished) return null;

        // TODO make there be a parameter for max messages rather than just doing 100.
        Peers peers = new Peers(collector);

        // Randomly arrange the list of peers.
        // First, put all peers in an array.
        ArrayList<Identity> identities = new ArrayList<>();
        identities.addAll(addrs);

        // Then randomly select them one at a time and put them in peers.
        for (int rmax = addrs.size() - 1; rmax >= 0; rmax--) {
            int rand = crypto.getRandom(rmax);
            Identity addr = identities.get(rand);

            // Put the address at the end into the spot we just took. This way,
            // we are always selecting randomly from a set of unselected peers.
            identities.set(rand, identities.get(rmax));

            // Don't try to connect to myself.
            //if (addr.equals(me)) continue;

            peers.queue(addr);
        }

        final Retries retries = new Retries();

        while (true) {
            Identity identity = peers.peek();
            if (identity == null) {
                break;
            }

            if (peers.connected(identity)) {
                peers.remove();
                continue;
            }

            Peer<Identity, P> peer = channel.getPeer(identity);

            if (peer == null) {
                // TODO clean up properly and fail more gracefully.
                throw new NullPointerException();
            }

            if (peers.openSession(identity, peer)) {
                continue;
            }

            int r = retries.increment(peer.identity());

            if (r > maxRetries) {
                // Maximum number of retries has prevented us from making all connections.
                // TODO In some instances, it should be possible to run coin shuffle with fewer
                // players, so we should still return the network object.
                return null;
            }

            // We were not able to connect to this peer this time,
            // so we move on to the next one for now.
            if (!peers.rotate()) {
                break;
            }
        }

        finished = true;
        return collector;
    }

    @Override
    public void close() throws InterruptedException {
        connection.close();
    }

    @Override
    public boolean closed() throws InterruptedException {
        return connection.closed();
    }
}
