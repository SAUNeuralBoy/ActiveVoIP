package team.genesis.android.activevoip.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import team.genesis.android.activevoip.VoIPService;
import team.genesis.android.activevoip.network.ClientTunnel;
import team.genesis.data.UUID;

public class MainViewModel extends ViewModel {
    private final MutableLiveData<CompassColor> compassColor;
    public enum CompassColor{
        FINE,DISTURBING,ERROR
    }
    public MainViewModel(){
        compassColor = new MutableLiveData<>(CompassColor.ERROR);
    }
    public MutableLiveData<CompassColor> getCompassColor() {
        return compassColor;
    }
}
