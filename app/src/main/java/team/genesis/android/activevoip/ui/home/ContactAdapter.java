package team.genesis.android.activevoip.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.MainActivity;
import team.genesis.android.activevoip.R;
import team.genesis.android.activevoip.data.Contact;
import team.genesis.android.activevoip.db.ContactEntity;
import team.genesis.data.UUID;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact,parent,false));
        holder.buttonRetry.setOnClickListener(v -> {
            Contact contact = new Contact();
            contact.alias = holder.contactAlias.getText().toString();
            contact.uuid = new UUID(Crypto.from64(holder.contactUUID.getText().toString()));
            mActivity.createPair(contact);
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contact contact;
        try {
            contact = mContactList.get(position).getContact();
        } catch (Crypto.DecryptException e) {
            return;
        }
        holder.contactAlias.setText(contact.alias);
        holder.contactUUID.setText(Crypto.to64(contact.uuid.getBytes()));
        switch (contact.status){
            case READY:
                holder.buttonCall.setVisibility(View.VISIBLE);
                holder.contactStatus.setVisibility(View.GONE);
                break;
            case CONFIRM_WAIT:
                holder.contactStatus.setText(R.string.wait_for_confirm);
                holder.contactFingerPrint.setText(Crypto.bytesToHex(contact.pkSHA256(),":"));
                break;
            case PAIR_SENT:
            case PAIR_RCVD:
                holder.contactStatus.setText(R.string.wait_for_response);
                break;
        }
        if(contact.status== Contact.Status.PAIR_SENT||contact.status== Contact.Status.PAIR_RCVD)
            holder.buttonRetry.setVisibility(View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return mContactList ==null?0: mContactList.size();
    }

    private List<ContactEntity> mContactList;
    private final MainActivity mActivity;
    public ContactAdapter(MainActivity activity, List<ContactEntity> contacts){
        mActivity = activity;
        setContactList(contacts);
    }
    public void setContactList(List<ContactEntity> contacts){
        mContactList = contacts;
    }
    static class ViewHolder extends RecyclerView.ViewHolder{
        TextView contactAlias;
        TextView contactUUID;
        TextView contactStatus;
        TextView contactFingerPrint;
        ImageButton buttonCall;
        ImageButton buttonRetry;

        public ViewHolder (View view)
        {
            super(view);
            contactAlias = view.findViewById(R.id.contact_alias);
            contactUUID = view.findViewById(R.id.contact_uuid);
            buttonCall = view.findViewById(R.id.button_call_contact);
            contactStatus = view.findViewById(R.id.contact_status);
            contactFingerPrint = view.findViewById(R.id.contact_fingerprint);
            buttonRetry = view.findViewById(R.id.button_pair_retry);
        }


    }
}
