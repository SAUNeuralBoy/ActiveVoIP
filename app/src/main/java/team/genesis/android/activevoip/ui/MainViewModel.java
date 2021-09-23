package team.genesis.android.activevoip.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import team.genesis.android.activevoip.network.ClientTunnel;
import team.genesis.data.UUID;

public class MainViewModel extends ViewModel {
    private final MutableLiveData<CompassColor> compassColor;
    private final MutableLiveData<ClientTunnel.Preference> prefLiveData;
    public enum CompassColor{
        FINE,DISTURBING,ERROR
    }
    public MainViewModel(){
        compassColor = new MutableLiveData<>(CompassColor.ERROR);
        prefLiveData = new MutableLiveData<>(new ClientTunnel.Preference(new UUID(),"127.0.0.1",10113));
    }
    public MutableLiveData<CompassColor> getCompassColor() {
        return compassColor;
    }
    public MutableLiveData<ClientTunnel.Preference> getPrefLiveData(){
        return prefLiveData;
    }

}
