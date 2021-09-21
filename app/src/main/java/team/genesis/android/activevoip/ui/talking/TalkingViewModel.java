package team.genesis.android.activevoip.ui.talking;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import team.genesis.android.activevoip.data.Contact;

public class TalkingViewModel extends ViewModel {
    private Contact mContact;
    private byte[] mOtherPk;
    private byte[] mOurPk;
    private final MutableLiveData<Status> mLiveStatus;
    public TalkingViewModel(){
        mLiveStatus = new MutableLiveData<>(Status.DEAD);

    }
    public void setOtherPk(byte[] otherPk){
        mOtherPk = otherPk;
    }
    public byte[] getOtherPk(){
        return mOtherPk;
    }
    public void setOurPk(byte[] ourPk){
        mOurPk = ourPk;
    }
    public byte[] getOurPk(){
        return mOurPk;
    }
    public void setStatus(Status status){
        mLiveStatus.setValue(status);
    }
    public Status getStatus(){
        return mLiveStatus.getValue();
    }
    public MutableLiveData<Status> getLiveStatus(){
        return mLiveStatus;
    }
    public void setContact(Contact contact){
        mContact = contact;
    }
    public Contact getContact(){
        return mContact;
    }
    public enum Status{
        CALLING,INVOKING,INCOMING,REJECTED, ACCEPT_CALL,CALL_ACCEPTED,TALKING,DEAD
    }
}
