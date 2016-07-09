/**
 *
 * Copyright © 2016 Mycelium.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 *
 */

package com.shuffle.moderator;

import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.chan.BasicChan;
import com.shuffle.chan.Chan;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * The coordinator attempts to get mixes started that are ready to go and passes messages
 * among participants.
 *
 * TODO upgrade from String for message type.
 *
 * Created by Daniel Krawisz on 12/26/15.
 */
public class Moderator {
    // The default time a peer will wait before reporting a timeout to the coordinator.
    public static final long DEFAULT_TIMEOUT = 30 * 1000;

    // The minimum time that bust be allowed before the protocol can begin.
    public static final long DEFAULT_MIN_WAIT = 60 * 1000;

    private final SecureRandom random;

    // Pending mixes, sorted by begin time.
    private final SortedSet<Mix<String>> pending = new TreeSet<>(new Comparator<Mix>() {
        @Override
        public int compare(Mix a, Mix b) {
            if (a.begin < b.begin) return -1;
            if (a.begin > b.begin) return 1;
            return 0;
        }
    });

    // Mixes that will run as soon as they fill up, sorted by expiration.
    private final SortedSet<Mix<String>> immediate = new TreeSet<>(new Comparator<Mix>() {
        @Override
        public int compare(Mix a, Mix b) {
            if (a.expiration < b.expiration) return -1;
            if (a.expiration > b.expiration) return 1;
            return 0;
        }
    });

    // Mixes that are open, ie, not currently being run.
    private Map<String, Mix<String>> open = new HashMap<>();

    // A lock for managing the previous three data structures.
    private final Object lock = new Object();

    // Maximum number of protocols that can be open at once.
    public final int maxOpen;

    // Maximum number of protocols that can be moderated at once.
    public final int max;

    // Channel for sending mixes that need to be handled right away.
    private final Chan<Mix<String>> update = new BasicChan<>(2);

    // The next mix that will be handled.
    private Mix<String> next = null;

    public String createMix(
            int minPlayers, int maxPlayers, int maxRetries,
            long amount, long begin, long expiration,
            VerificationKey key,
            Chan<String> chan) throws IOException, InterruptedException {

        // No transactions less than five minutes from now.
        long timeLimit = System.currentTimeMillis() + DEFAULT_MIN_WAIT;

        if (begin < timeLimit) {
            throw new IllegalArgumentException("New mixes must take place at least 5 minutes from now.");
        }

        String id = new String(random.generateSeed(16));

        Mix<String> mix =
                new Mix<>(
                        id, minPlayers, maxPlayers, maxRetries, amount, begin, expiration, DEFAULT_TIMEOUT);

        mix.put(key, chan);

        synchronized (lock) {
            Mix duplicate = open.get(mix.id);
            if (duplicate != null) throw new IllegalArgumentException("Mix already exists");

            // Is max pending reached?
            if (open.size() >= maxOpen) {
                throw new IllegalArgumentException("Too many pending mixes.");
            }

            open.put(mix.id, mix);

            if (next == null || next.begin < mix.begin) {
                update.send(mix);
            }

            pending.add(mix);
        }

        return id;
    }

    public void register(String id, VerificationKey key, Chan<String> chan)
            throws IOException, InterruptedException {

        // Do we have a pending mix with this id?
        synchronized (lock) {
            Mix<String> mix = open.get(id);

            if (mix == null) throw new IllegalArgumentException("No such mix.");

            if(chan.closed()) throw new IllegalArgumentException("Connection closed.");

            mix.put(key, chan);

            if (immediate.contains(mix)) {
                if (mix.ready()) {

                    // Begin immediately.
                    if (System.currentTimeMillis() > mix.begin) {
                        immediate.remove(mix);
                        update.send(mix);
                    }
                }
            }
        }
    }

    public void reconnect(String id, VerificationKey key, Chan<String> chan)
            throws IOException, InterruptedException {

        // Do we have a pending mix with this id?
        synchronized (lock) {
            Mix<String> mix = open.get(id);

            if (mix == null) throw new IllegalArgumentException("No such mix.");

            if(chan.closed()) throw new IllegalArgumentException("Connection closed.");

            mix.put(key, chan);

            if (immediate.contains(mix)) {
                if (mix.ready()) {

                    // Begin immediately.
                    if (System.currentTimeMillis() > mix.begin) {
                        immediate.remove(mix);
                        update.send(mix);
                    }
                }
            }
        }
    }

    public Moderator(int maxOpen, int max, SecureRandom random, final Executor exec) {
        this.random = random;
        this.maxOpen = maxOpen;
        this.max = max;

        // Channel to send mixes that are ready to start immediately
        // as soon as enough people sign up.
        final Chan<Mix<String>> ready = new BasicChan<>();

        // Channel to send reports once a protocol is done.
        final Chan<String> report = new BasicChan<>();

        // This is the thread that handles pending protocols.
        exec.execute(new Runnable() {
            @Override
            public void run() {

                try {
                    while (true) {
                        if (next == null) {
                            if (pending.size() == 0) {
                                next = update.receive();
                            } else {
                                synchronized (lock) {
                                    next = pending.first();
                                    pending.remove(next);
                                }
                            }
                        }

                        long wait = next.begin - System.currentTimeMillis();

                        // Wait until it is time.
                        if (wait > 0) {
                            Mix<String> updated = update.receive(wait, TimeUnit.MILLISECONDS);

                            if (updated != null) {
                                synchronized (lock) {
                                    pending.add(next);
                                }
                                next = updated;
                                continue;
                            }
                        }

                        if (next.ready()) {
                            next.run(exec, report);
                        } else {
                            ready.send(next);
                        }
                        next = null;
                    }
                } catch (InterruptedException e) {
                    // TODO: notify everyone that we are shutting down.
                } catch (IOException e) {
                    // This should not happen.
                    e.printStackTrace();
                }
            }
        });

        // The thread that manages protocols which will begin immediately
        // once enough people sign up.
        exec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Mix<String> next = null;
                    while (true) {

                        // First check for the first element ready to expire.
                        synchronized (lock) {
                            if (immediate.size() != 0) {
                                next = immediate.first();
                            }
                        }

                        // if there is none, wait for something to show up.
                        if (next == null) {
                            immediate.add(ready.receive());
                            continue;
                        }

                        // How long do we have to wait before it expires?
                        long wait = next.expiration - System.currentTimeMillis();

                        if (wait >= 0) {
                            // Wait for something new to arrive, or until we're done waiting.
                            Mix<String> updated = update.receive(wait, TimeUnit.MILLISECONDS);
                            if (updated != null) {
                                synchronized (lock) {
                                    immediate.add(updated);
                                }

                                continue;
                            }
                        }

                        synchronized (lock) {
                            // It might have been removed in the meantime.
                            if (immediate.contains(next)) {
                                open.remove(next.id);
                                immediate.remove(next);
                            }
                        }
                    }
                } catch (InterruptedException e) {

                }
            }
        });

        // This is the thread that reports the results of protocols once they are completed.
        exec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        String r = report.receive();
                    }
                } catch (InterruptedException e) {

                }
            }
        });
    }
}
