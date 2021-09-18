package team.genesis.android.activevoip.data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import team.genesis.data.UUID;

public class Contact {
    public String alias;
    public UUID uuid;
    public Status status;
    public enum Status{
        PAIR_SENT,PAIR_RCVD,CONFIRM_WAIT,READY
    }
    public byte[] ourPk;
    public byte[] otherPk;
    public Contact(){
        otherPk = new byte[91];
    }
}
