package team.genesis.android.activevoip.ui.talking;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import team.genesis.android.activevoip.data.Contact;

public class TalkingViewModel extends ViewModel {
    private Contact mContact;
    private Status mStatus;
    private byte[] mOtherPk;
    private byte[] mOurPk;
    private final MutableLiveData<Boolean> mReadyToTalk;
    public TalkingViewModel(){
        mStatus = Status.DEAD;
        mReadyToTalk = new MutableLiveData<>(false);

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
        mStatus = status;
    }
    public Status getStatus(){
        return mStatus;
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
    public void setReadyToTalk(boolean ready){
        if(mReadyToTalk.getValue()==null|| mReadyToTalk.getValue()!=ready) mReadyToTalk.setValue(ready);
    }
    public MutableLiveData<Boolean> isReadyToTalk(){
        return mReadyToTalk;
    }
}
