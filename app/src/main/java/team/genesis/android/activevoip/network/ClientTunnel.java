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

public class ClientTunnel extends UDPActiveDatagramTunnel {

    private final Handler writeHandler,recvHandler,dnsHandler;
    private RecvListener mListener;

    private String mHostName;

    public ClientTunnel(String threadPrefix,InetAddress hostAddr, int port, UUID src) throws SocketException {
        super(hostAddr,port,src);

        writeHandler = UI.getCycledHandler(threadPrefix +"write");
        recvHandler = UI.getCycledHandler(threadPrefix +"recv");
        dnsHandler = UI.getCycledHandler(threadPrefix +"dns");
        mListener = msg -> {};
        mHostName = "127.0.0.1";
        Runnable keepsAlive = new Runnable() {
            @Override
            public void run() {
                try {
                    keepAlive();
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
                    mListener.onRecv(recv());
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
        if(dns.getIP()!=null){
            setHost(dns.getIP());
        }
    }
    public void update(String hostName){
        mHostName = hostName;
        dnsHandler.post(this::updateDNS);
    }
    @Override
    public void send(byte[] msg,UUID dst,UUID src){
        writeHandler.post(()-> {
            try {
                super.send(msg,dst,src);
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
