package team.genesis.android.activevoip.data;

import java.nio.ByteBuffer;

import team.genesis.data.UUID;

public class Contact {
    public String alias;
    public UUID uuid;
    public Status status;
    public enum Status{
        CONFIRM_WAIT,READY
    }
    public byte[] getBytes(){
        
    }
}
