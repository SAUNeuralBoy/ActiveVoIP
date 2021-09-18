package team.genesis.android.activevoip.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import team.genesis.android.activevoip.db.ContactEntity;

public class HomeViewModel extends ViewModel {

    private MutableLiveData<String> mText;
    private LiveData<List<ContactEntity>> mContacts;

    public HomeViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is home fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
    public LiveData<List<ContactEntity>> getContacts(){
        return mContacts;
    }
    public void setContacts(LiveData<List<ContactEntity>> contacts){
        mContacts = contacts;
    }
}