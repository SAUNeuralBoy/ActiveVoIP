package team.genesis.android.activevoip;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import team.genesis.data.UUID;

public class SPManager {
    private SharedPreferences sp;
    private SharedPreferences.Editor editor;
    public SPManager(Context context){
        try {
            sp = EncryptedSharedPreferences.create(
                    sharedPrefsFile,
                    keystoreAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            editor = sp.edit();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            sp = null;
            editor = null;
            System.exit(0);
        }
    }
    public static SPManager getManager(Context context){
        return new SPManager(context);
    }
    public void commit(){
        editor.commit();
    }
    public UUID getUUID(){
        String id64 = getUUID64();
        if(id64.equals("")) return null;
        return new UUID(Crypto.from64(id64));
    }
    public String getUUID64(){
        String id64 = sp.getString(keyUUID,"");
        if(id64.equals("")){
            setUUID(Crypto.randomUUID());
            commit();
        }
        return sp.getString(keyUUID,"");
    }
    public void setUUID(String id64){
        editor.putString(keyUUID,id64);
    }
    public void setUUID(UUID uuid){
        setUUID(Crypto.to64(uuid.getBytes()));
    }
    public String getHostname(){
        return sp.getString(keyHostname,"");
    }
    public void setHostname(String hostname){
        editor.putString(keyHostname,hostname);
    }
    public int getPort(){
        int port = sp.getInt(keyPort,-1);
        if(port==-1){
            setPort(10113);
            commit();
            return 10113;
        }
        return port;
    }
    public void setPort(int port){
        editor.putInt(keyPort,port);
    }
    private static final String sharedPrefsFile = "settings.bin";
    private static final String keystoreAlias = "keystore_preference";
    private static final String keyUUID = "key_uuid";
    private static final String keyHostname = "key_hostname";
    private static final String keyPort = "key_port";
}
