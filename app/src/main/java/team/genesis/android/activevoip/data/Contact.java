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
        CONFIRM_WAIT,READY
    }
}
