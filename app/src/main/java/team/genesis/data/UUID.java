package team.genesis.data;

import androidx.annotation.Nullable;

import java.security.SecureRandom;
import java.util.Arrays;

public class UUID {
    public static int LEN = 16;
    private byte[] id = new byte[LEN];
    void randomize(){
        new SecureRandom().nextBytes(id);
    }
    public byte[] getBytes(){
        return id;
    }
    public UUID(){
        randomize();
    }
    public void fromBytes(byte[] bytes){
        System.arraycopy(bytes,0,id,0, LEN);
    }
    public void fromBytes(byte[] bytes,int offset){
        fromBytes(Arrays.copyOfRange(bytes,offset,offset+ LEN));
    }
    public UUID(byte[] bytes){
        fromBytes(bytes);
    }
    public UUID(byte[] bytes,int offset){
        fromBytes(bytes,offset);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(!(obj instanceof UUID)) return false;
        return Arrays.equals(getBytes(),((UUID)obj).getBytes());
    }
}
