package team.genesis.android.activevoip.ui.talking;

import androidx.lifecycle.ViewModel;

import team.genesis.android.activevoip.data.Contact;

public class TalkingViewModel extends ViewModel {
    private Contact mContact;
    private Status mStatus;
    private byte[] mOtherPk;
    public TalkingViewModel(){
        mStatus = Status.DEAD;
    }
    public void setOtherPk(byte[] otherPk){
        mOtherPk = otherPk;
    }
    public byte[] getOtherPk(){
        return mOtherPk;
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
        CALLING,INVOKING,INCOMING,REJECTED,TALKING,DEAD
    }
}
