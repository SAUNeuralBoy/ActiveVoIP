package team.genesis.android.activevoip.network;

import android.os.Handler;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;

import team.genesis.android.activevoip.UI;
import team.genesis.data.UUID;
import team.genesis.network.DNSLookupThread;
import team.genesis.tunnels.ActiveDatagramTunnel;
import team.genesis.tunnels.UDPActiveDatagramTunnel;

import static java.lang.System.exit;

public class ClientTunnel {

    private final Handler writeHandler,recvHandler,dnsHandler;
    private RecvListener mListener;

    private UDPActiveDatagramTunnel tunnel;

    private String mHostName;
    private InetAddress mHost;

    public ClientTunnel(String threadPrefix){
        try {
            tunnel = new UDPActiveDatagramTunnel(InetAddress.getLoopbackAddress(),10113,new UUID());
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
        }
        writeHandler = UI.getCycledHandler(threadPrefix +"write");
        recvHandler = UI.getCycledHandler(threadPrefix +"recv");
        dnsHandler = UI.getCycledHandler(threadPrefix +"dns");
        mListener = msg -> {};
        mHostName = "127.0.0.1";
        mHost=InetAddress.getLoopbackAddress();
        Runnable keepsAlive = new Runnable() {
            @Override
            public void run() {
                try {
                    tunnel.keepAlive();
                } catch (IOException ignored) {
                }
                writeHandler.postDelayed(this,5000);
            }
        };
        writeHandler.postDelayed(keepsAlive,1000);
        Runnable recv = new Runnable() {
            @Override
            public void run() {
                try {
                    mListener.onRecv(tunnel.recv());
                } catch (IOException e) {
                    recvHandler.postDelayed(this,1000);
                    return;
                }
                recvHandler.post(this);
            }
        };
        recvHandler.post(recv);
        Runnable dnsQuery = new Runnable() {
            @Override
            public void run() {
                updateDNS();
                dnsHandler.postDelayed(this,30000);
            }
        };
        dnsHandler.post(dnsQuery);
    }
    private void updateDNS(){
        DNSLookupThread dns = new DNSLookupThread(mHostName);
        dns.start();
        try {
            dns.join(1000);
        } catch (InterruptedException ignored) {
        }
        if(dns.getIP()!=null&&dns.getIP()!=mHost){
            mHost = dns.getIP();
            tunnel.setHost(dns.getIP());
        }
    }
    public void update(String hostName, int port){
        mHostName = hostName;
        tunnel.setHost(mHost,port);
        dnsHandler.post(this::updateDNS);
    }
    public void write(byte[] msg,UUID dst,UUID src){
        writeHandler.post(()-> {
            try {
                tunnel.send(msg,dst,src);
            } catch (IOException ignored) {
            }
        });
    }
    public void setRecvListener(RecvListener listener){
        mListener = listener;
    }

    public interface RecvListener{
        void onRecv(ActiveDatagramTunnel.Incoming msg);
    }
}