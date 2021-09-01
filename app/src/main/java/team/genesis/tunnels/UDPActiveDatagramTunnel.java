package team.genesis.tunnels;

import team.genesis.data.UUID;
import team.genesis.tunnels.active.datagram.udp.UDPProbe;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;

public class UDPActiveDatagramTunnel extends ActiveDatagramTunnel implements Runnable{
    public static final int PACK_LEN = 32768;
    public static final long TTL = 30000;
    private UUID src;
    private final DatagramSocket sock;
    private InetAddress hostAddr;
    private int port;
    private long interval;
    private int alivePacketCount;
    private Thread aliveThread;
    public UDPActiveDatagramTunnel(InetAddress hostAddr, int port, UUID src) throws SocketException {
        setSrc(src);
        sock = new DatagramSocket();
        setHost(hostAddr, port);
        setAlive(TTL/5,1);
        aliveThread = new Thread(this);
    }

    @Override
    public void send(byte[] data, UUID dst, UUID src) throws IOException{
        if(data.length>PACK_LEN-1-16-16)   throw new IllegalArgumentException("Should Fragment");
        byte[] pack = new byte[data.length+1+16+16];
        pack[0] = 1;
        System.arraycopy(src.getBytes(),0,pack,1,16);
        System.arraycopy(dst.getBytes(),0,pack,1+16,16);
        System.arraycopy(data,0,pack,1+16+16,data.length);
        sendPacket(pack);
    }

    @Override
    public Incoming recv() throws IOException {
        byte[] buf = new byte[PACK_LEN];
        DatagramPacket packet = new DatagramPacket(buf, PACK_LEN);
        do {
            sock.receive(packet);
        }
        while ((!packet.getAddress().equals(hostAddr))||packet.getPort()!=port||packet.getLength()<=1+16||packet.getData()[0]!=2);
        Incoming msg = new Incoming();
        msg.src = new UUID(packet.getData(),1);
        msg.data = Arrays.copyOfRange(packet.getData(),1+16,packet.getLength());
        return msg;
    }
    public void keepAlive() throws IOException {
        byte[] pack = new byte[1+16];
        pack[0] = 2;
        System.arraycopy(src.getBytes(),0,pack,1,16);
        sendPacket(pack);
    }


    public void send(byte[] data,UUID dst) throws IOException{
        send(data,dst,src);
    }
    public void setSrc(UUID src){
        this.src = src;
    }
    public void setHost(InetAddress hostAddr,int port){
        this.hostAddr = hostAddr;
        this.port = port;
    }
    private void sendPacket(byte[] pack) throws IOException {
        DatagramPacket packet = new DatagramPacket(pack, pack.length, hostAddr, port);
        sock.send(packet);
    }
    public static UDPProbe getProbe(int timeOut) throws SocketException {
        return new UDPProbe(timeOut);
    }

    public void setAlive(long interval,int count){
        this.interval = interval;
        this.alivePacketCount = count;
    }
    @Override
    public void run() {
        while (true) {
            try {
                for (int i = 0; i < alivePacketCount; i++)
                    keepAlive();
                //noinspection BusyWait
                Thread.sleep(interval);
            } catch (InterruptedException | IOException e) {
                return;
            }
        }
    }
    public boolean startAliveThread(){
        if(isThreadAlive())   return false;
        if(aliveThread.getState() != Thread.State.NEW)
            aliveThread = new Thread(this);
        aliveThread.start();
        return true;
    }
    public void stopAliveThread(){
        if(isThreadAlive())
            aliveThread.interrupt();
    }
    public boolean isThreadAlive(){
        return aliveThread.isAlive();
    }
}
