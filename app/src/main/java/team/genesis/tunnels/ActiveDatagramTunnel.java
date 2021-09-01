package team.genesis.tunnels;

import team.genesis.data.UUID;

import java.io.IOException;

public abstract class ActiveDatagramTunnel {
    public abstract void send(byte[] data,UUID dst,UUID src) throws IOException;
    public abstract Incoming recv() throws IOException;

    public static class Incoming{
        public byte[] data;
        public UUID src;
    }
}
