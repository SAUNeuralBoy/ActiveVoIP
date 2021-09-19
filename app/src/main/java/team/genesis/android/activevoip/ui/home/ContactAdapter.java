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
import team.genesis.android.activevoip.db.ContactDB;
import team.genesis.android.activevoip.db.ContactDao;
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
        holder.buttonAccept.setOnClickListener(v -> {
            ContactDao dao = mActivity.getDao();
            ContactEntity[] result = ContactDB.findContactByUUID(dao,new UUID(Crypto.from64(holder.contactUUID.getText().toString())));
            if(result==null)    return;
            Contact contact;
            try {
                contact = result[0].getContact();
            } catch (Crypto.DecryptException e) {
                dao.deleteContact(result[0]);
                return;
            }
            contact.status = Contact.Status.READY;
            dao.insertContact(new ContactEntity(contact));
        });
        holder.buttonReject.setOnClickListener(v -> {
            ContactDao dao = mActivity.getDao();
            ContactEntity[] result = ContactDB.findContactByUUID(dao,new UUID(Crypto.from64(holder.contactUUID.getText().toString())));
            if(result==null)    return;
            dao.deleteContact(result[0]);
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
                holder.contactFingerPrint.setText(Crypto.bytesToHex(contact.pkSHA256(),":"));
                holder.contactFingerPrint.setVisibility(View.VISIBLE);
                holder.contactStatus.setVisibility(View.GONE);
                holder.buttonRetry.setVisibility(View.GONE);
                holder.buttonAccept.setVisibility(View.GONE);
                holder.buttonReject.setVisibility(View.GONE);
                holder.buttonCall.setVisibility(View.VISIBLE);
                break;
            case CONFIRM_WAIT:
                //holder.contactStatus.setText(R.string.wait_for_confirm);
                holder.contactFingerPrint.setText(Crypto.bytesToHex(contact.pkSHA256(),":"));
                holder.contactFingerPrint.setVisibility(View.VISIBLE);
                holder.contactStatus.setVisibility(View.GONE);
                holder.buttonRetry.setVisibility(View.GONE);
                holder.buttonAccept.setVisibility(View.VISIBLE);
                holder.buttonReject.setVisibility(View.VISIBLE);
                holder.buttonCall.setVisibility(View.GONE);
                break;
            case PAIR_SENT:
            case PAIR_RCVD:
                holder.contactStatus.setText(R.string.wait_for_response);
                holder.contactFingerPrint.setVisibility(View.GONE);
                holder.contactStatus.setVisibility(View.VISIBLE);
                holder.buttonRetry.setVisibility(View.VISIBLE);
                holder.buttonAccept.setVisibility(View.GONE);
                holder.buttonReject.setVisibility(View.GONE);
                holder.buttonCall.setVisibility(View.GONE);
                break;
        }
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
        ImageButton buttonAccept;
        ImageButton buttonReject;

        public ViewHolder (View view)
        {
            super(view);
            contactAlias = view.findViewById(R.id.contact_alias);
            contactUUID = view.findViewById(R.id.contact_uuid);
            buttonCall = view.findViewById(R.id.button_call_contact);
            contactStatus = view.findViewById(R.id.contact_status);
            contactFingerPrint = view.findViewById(R.id.contact_fingerprint);
            buttonRetry = view.findViewById(R.id.button_pair_retry);
            buttonAccept = view.findViewById(R.id.button_pair_accept);
            buttonReject = view.findViewById(R.id.button_pair_reject);
        }


    }
}
