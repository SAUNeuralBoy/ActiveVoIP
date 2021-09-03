package team.genesis.android.activevoip;

import android.util.Base64;

import java.security.SecureRandom;

import team.genesis.data.UUID;

public class Crypto {
    public static String to64(byte[] bytes){
        return Base64.encodeToString(bytes,Base64.NO_WRAP);
    }
    public static byte[] from64(String s){
        return Base64.decode(s,Base64.NO_WRAP);
    }
    public static UUID randomUUID(){
        byte[] randBytes = new byte[16];
        new SecureRandom().nextBytes(randBytes);
        return new UUID(randBytes);
    }
}
