package team.genesis.android.activevoip;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import javax.crypto.SecretKey;

import team.genesis.data.UUID;

public class VoIPService extends Service {
    private UUID ourId;
    private UUID otherId;
    private SecretKey secretKey;
    public void init(UUID ourId,UUID otherId,SecretKey secretKey){
        this.ourId = ourId;
        this.otherId = otherId;
        this.secretKey = secretKey;
    }
    public class VoIPBinder extends Binder{
        public VoIPService getService(){
            return VoIPService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }
}