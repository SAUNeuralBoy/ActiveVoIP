package team.genesis.tunnels.active.datagram.udp;

import java.io.IOException;
import java.net.*;

public class UDPProbe {
    private final DatagramSocket sock;
    private final byte[] pack;
    private final byte[] buf;
    private static final byte SUCCESS = 1;
    public UDPProbe(int timeOut) throws SocketException {
        sock = new DatagramSocket();
        sock.setSoTimeout(timeOut);
        pack = new byte[1];
        buf = new byte[1];
    }
    public int probe(InetAddress hostAddr, int port, int count) throws IOException {
        for(int i=0;i<count;i++) {
            DatagramPacket packet = new DatagramPacket(pack,1,hostAddr,port);
            sock.send(packet);
        }
        for(int i=0;i<count;i++){
            try {
                DatagramPacket recv = new DatagramPacket(buf,1);
                sock.receive(recv);
                if((!recv.getAddress().equals(hostAddr))||recv.getPort()!=port)   i--;
                if(recv.getLength()!=1||recv.getData()[0]!=SUCCESS)   return -1;
            }catch(SocketTimeoutException e){
                return i;
            }
        }
        return count;
    }
}
