package team.genesis.android.activevoip.ui.talking;

import androidx.lifecycle.ViewModel;

import team.genesis.android.activevoip.data.Contact;

public class TalkingViewModel extends ViewModel {
    private Contact mContact;
    public void setContact(Contact contact){
        mContact = contact;
    }
    public Contact getContact(){
        return mContact;
    }
}
