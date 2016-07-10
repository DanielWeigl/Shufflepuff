package com.shuffle.chan;

import java.io.Serializable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A chan that collects multiple inputs together into one output.
 *
 * Created by Daniel Krawisz on 5/18/16.
 */
public class Inbox<Address, X extends Serializable> implements Receive<Inbox.Envelope<Address, X>> {

    public static class Envelope<Address, X> implements Comparable<Envelope<Address, X>> {
        public final Address from;
        public final X payload;
        public final long received;

        Envelope(Address from, X payload) {
            if (from == null || payload == null) throw new NullPointerException();

            this.from = from;
            this.payload = payload;
            this.received = System.currentTimeMillis();
        }

        @Override
        public int compareTo(Envelope<Address, X> envelope) {
            if (received == envelope.received) return 0;

            if (received < envelope.received) return -1;

            return 1;
        }

        @Override
        public String toString() {
            return "[ received: " + received + "; from: " + from + "; contents: " + payload + " ]";
        }
    }

    private static class Transit<Address, X> {
        public final Envelope<Address, X> m;

        private Transit(Envelope<Address, X> m) {
            this.m = m;
        }

        // Used to represent that the channel was closed.
        private Transit() {
            this.m = null;
        }

        @Override
        public String toString() {
            return "Tr[" + m + "]";
        }
    }

    private final LinkedBlockingQueue<Transit<Address, X>> q;

    public Inbox(int cap) {
        q = new LinkedBlockingQueue<>(cap);
    }

    private boolean closed = false;
    private boolean closeSent = false;

    private class Receiver implements Send<X> {
        private final Address from;
        private boolean closed = false;

        private Receiver(Address from) {
            this.from = from;
        }

        @Override
        public boolean send(X x) throws InterruptedException {

            return !(closed || Inbox.this.closed) &&
                    q.add(new Transit<Address, X>(new Inbox.Envelope<Address, X>(from, x)));
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    // A send into the inbox is opened, enabling someone to dump messages in it.
    public synchronized Send<X> receivesFrom(Address from) {
        if (from == null) throw new NullPointerException();

        if (closed) return null;

        return new Receiver(from);
    }

    public void close() throws InterruptedException {
        closed = true;
        closeSent = q.offer(new Transit<Address, X>());
    }

    private Envelope<Address, X> receiveMessage(Transit<Address, X> m) {

        if (m == null) return null;

        if (closed && !closeSent) {
            // There is definitely room in the queue because we just removed
            // one element and no more were allowed to be put in.
            q.add(new Transit<Address, X>());
            closeSent = true;
        }

        return m.m;
    }

    @Override
    public Envelope<Address, X> receive() throws InterruptedException {
        if (closed && q.size() == 0) {
            return null;
        }

        return receiveMessage(q.take());
    }

    @Override
    public Envelope<Address, X> receive(long l, TimeUnit u) throws InterruptedException {

        if (closed && q.size() == 0) {
            return null;
        }
        return receiveMessage(q.poll(l, u));
    }

    @Override
    public boolean closed() {
        return closed;
    }

    @Override
    public String toString() {
        return "Inbox[]";
    }
}
