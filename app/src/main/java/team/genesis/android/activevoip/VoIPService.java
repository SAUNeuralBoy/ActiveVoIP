package team.genesis.android.activevoip;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class VoIPService extends Service {
    public VoIPService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}