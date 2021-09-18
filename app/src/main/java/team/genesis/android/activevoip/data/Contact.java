package team.genesis.android.activevoip.data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import team.genesis.data.UUID;

import static java.lang.System.exit;

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
    public byte[] pkSHA256(){
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            exit(0);
            return null;
        }
        sha.update(ourPk);
        return sha.digest(otherPk);
    }
}
