package team.genesis.network;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class DNSLookupThread extends Thread {
    private InetAddress addr;
    private final String hostname;

    public DNSLookupThread(String hostname) {
        this.hostname = hostname;
    }

    @Override
    public void run() {
        try {
            InetAddress add = InetAddress.getByName(hostname);
            set(add);
        } catch (UnknownHostException e) {
            set(null);
        }
    }

    private synchronized void set(InetAddress addr) {
        this.addr = addr;
    }

    public synchronized InetAddress getIP() {
        if (null != this.addr) {
            return addr;
        }

        return null;
    }
}
