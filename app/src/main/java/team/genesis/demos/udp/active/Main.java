package team.genesis.demos.udp.active;

import team.genesis.data.UUID;
import team.genesis.tunnels.UDPActiveDatagramTunnel;
import team.genesis.tunnels.active.datagram.udp.UDPProbe;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) throws IOException {
	// write your code here
        String hostName = args[0];
        int port = Integer.parseInt(args[1],10);
        UUID uuid = new UUID(args[2].getBytes(StandardCharsets.ISO_8859_1));
        UDPActiveDatagramTunnel tunnel = new UDPActiveDatagramTunnel(InetAddress.getByName(hostName),port,uuid);
        tunnel.keepAlive();
        tunnel.startAliveThread();
        for(;;)
            System.out.println(new String(tunnel.recv().data,StandardCharsets.UTF_8));
    }
    public static class Main2{
        public static void main(String[] args) throws IOException {
            // write your code here
            String hostName = args[0];
            int port = Integer.parseInt(args[1],10);
            UUID uuid = new UUID(args[2].getBytes(StandardCharsets.ISO_8859_1));
            UDPActiveDatagramTunnel tunnel = new UDPActiveDatagramTunnel(InetAddress.getByName(hostName),port,uuid);
            tunnel.send("hello!".getBytes(StandardCharsets.UTF_8),new UUID(args[3].getBytes(StandardCharsets.ISO_8859_1)));
        }
    }
}
