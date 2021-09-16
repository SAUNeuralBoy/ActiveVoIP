package team.genesis.android.activevoip.ui;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
    private final MutableLiveData<CompassColor> compassColor;
    public enum CompassColor{
        FINE,DISTURBING,ERROR
    }
    public MainViewModel(){
        compassColor = new MutableLiveData<CompassColor>(CompassColor.ERROR);
    }
    public MutableLiveData<CompassColor> getCompassColor() {
        return compassColor;
    }
}
