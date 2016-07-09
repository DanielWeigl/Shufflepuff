package com.shuffle.p2p;

import com.shuffle.chan.Send;
import com.shuffle.chan.packet.Marshaller;
import com.shuffle.protocol.FormatException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by Daniel Krawisz on 5/30/16.
 */
public class MarshallChannel<Address, X extends Serializable> implements Channel<Address, X> {
    private static final Logger log = LogManager.getLogger(MarshallChannel.class);

    private final Channel<Address, Bytestring> inner;
    private final Marshaller<X> m;

    public MarshallChannel(Channel<Address, Bytestring> inner, Marshaller<X> m) {
        this.inner = inner;
        this.m = m;
    }

    private class MarshallSession implements Session<Address, X> {
        private final Session<Address, Bytestring> s;

        private MarshallSession(Session<Address, Bytestring> s) {
            this.s = s;
        }

        @Override
        public boolean closed() {
            return s.closed();
        }

        @Override
        public Peer<Address, X> peer() {
            return new MarshallPeer(s.peer());
        }

        @Override
        public boolean send(X x) throws InterruptedException, IOException {
            Bytestring b = m.marshall(x);
            return b != null && s.send(b);
        }

        @Override
        public void close() throws InterruptedException {
            s.close();
        }
    }

    // This class should not exist.
    private class UnmarshallSend implements Send<Bytestring> {
        private final Send<X> z;

        private UnmarshallSend(Send<X> z) {
            this.z = z;
        }

        @Override
        public boolean send(Bytestring b) throws InterruptedException,IOException {
            X x = null;
            try {
                x = m.unmarshall(b);
            } catch (FormatException e) {
                // We have been sent an invalid message.
                log.warn("Invalid message " + b + " sent: " + e.getMessage());
                return false;
            }
            return x != null && z.send(x);
        }

        @Override
        public void close() throws InterruptedException {
            z.close();
        }
    }

    private class MarshallPeer implements Peer<Address, X> {
        private final Peer<Address, Bytestring> p;

        private MarshallPeer(Peer<Address, Bytestring> p) {
            this.p = p;
        }

        @Override
        public Address identity() {
            return p.identity();
        }

        @Override
        public Session<Address, X> openSession(Send<X> send) throws InterruptedException, IOException {
            if (send == null) return null;
            Session<Address, Bytestring> s = p.openSession(new UnmarshallSend(send));
            if (s == null) return null;
            return new MarshallSession(s);
        }

        @Override
        public void close() throws InterruptedException {
            p.close();
        }
    }

    @Override
    public Peer<Address, X> getPeer(Address you) {
        Peer<Address, Bytestring> p = inner.getPeer(you);
        if (p == null) return null;
        return new MarshallPeer(p);
    }

    public class MarshallConnection implements Connection<Address> {
        private final Connection<Address> inner;

        public MarshallConnection(Connection<Address> inner) {
            this.inner = inner;
        }

        @Override
        public void close() throws InterruptedException {
            inner.close();
        }

        @Override
        public boolean closed() throws InterruptedException {
            return inner.closed();
        }
    }

    private class UnmarshallListener implements Listener<Address, Bytestring> {
        private final Listener<Address, X> l;

        private UnmarshallListener(Listener<Address, X> l) {
            this.l = l;
        }

        @Override
        public Send<Bytestring> newSession(Session<Address, Bytestring> session) throws InterruptedException {
            Send<X> z = l.newSession(new MarshallSession(session));
            if (z == null) return null;
            return new UnmarshallSend(z);
        }
    }

    @Override
    public Connection<Address> open(Listener<Address, X> listener) throws InterruptedException, IOException {
        return new MarshallConnection(inner.open(new UnmarshallListener(listener)));
    }
}
