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
import team.genesis.android.activevoip.R;
import team.genesis.android.activevoip.data.Contact;
import team.genesis.android.activevoip.db.ContactEntity;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> {
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact,parent,false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contact contact;
        try {
            contact = contactList.get(position).getContact();
        } catch (Crypto.DecryptException e) {
            return;
        }
        holder.contactAlias.setText(contact.alias);
        holder.contactUUID.setText(Crypto.to64(contact.uuid.getBytes()));
        switch (contact.status){
            case READY:
                //todo:onclick
                holder.buttonCall.setVisibility(View.VISIBLE);
                holder.contactStatus.setVisibility(View.GONE);
                break;
            case CONFIRM_WAIT:
                holder.contactStatus.setText(R.string.wait_for_confirm);
                break;
            case PAIR_SENT:
            case PAIR_RCVD:
                holder.contactStatus.setText(R.string.wait_for_response);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return contactList==null?0:contactList.size();
    }

    private List<ContactEntity> contactList;
    public ContactAdapter(List<ContactEntity> contacts){
        setContactList(contacts);
    }
    public void setContactList(List<ContactEntity> contacts){
        contactList = contacts;
    }
    static class ViewHolder extends RecyclerView.ViewHolder{
        TextView contactAlias;
        TextView contactUUID;
        TextView contactStatus;
        ImageButton buttonCall;

        public ViewHolder (View view)
        {
            super(view);
            contactAlias = view.findViewById(R.id.contact_alias);
            contactUUID = view.findViewById(R.id.contact_uuid);
            buttonCall = view.findViewById(R.id.button_call_contact);
            contactStatus = view.findViewById(R.id.contact_status);
        }


    }
}
