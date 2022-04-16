package team.genesis.android.activevoip.ui.home;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.MainActivity;
import team.genesis.android.activevoip.R;
import team.genesis.android.activevoip.UI;
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
            mActivity.getService().createPair(contact);
        });
        holder.buttonAccept.setOnClickListener(v -> {
            ContactDao dao = mActivity.getDao();
            Contact contact = ContactDB.getContactOrDelete(dao,new UUID(Crypto.from64(holder.contactUUID.getText().toString())));
            if(contact==null)   return;
            contact.status = Contact.Status.READY;
            dao.insertContact(new ContactEntity(contact));
        });
        holder.buttonReject.setOnClickListener(v -> {
            ContactDao dao = mActivity.getDao();
            ContactEntity result = ContactDB.findContactByUUID(dao,new UUID(Crypto.from64(holder.contactUUID.getText().toString())));
            if(result==null)    return;
            dao.deleteContact(result);
        });
        holder.buttonEdit.setOnClickListener(v->{
            ContactDao dao = mActivity.getDao();
            Contact contact = ContactDB.getContactOrDelete(dao,new UUID(Crypto.from64(holder.contactUUID.getText().toString())));
            if(contact==null)   return;
            final EditText input = new EditText(mActivity);
            input.setText(contact.alias);
            UI.makeInputWindow(mActivity, input, mActivity.getString(R.string.alias_input), (dialog, which) -> {
                contact.alias = input.getText().toString();
                dao.insertContact(new ContactEntity(contact));
            });
        });
        holder.buttonDelete.setOnClickListener(v -> {
            ContactDao dao = mActivity.getDao();
            ContactEntity result = ContactDB.findContactByUUID(dao,new UUID(Crypto.from64(holder.contactUUID.getText().toString())));
            if(result==null)    return;
            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setTitle(R.string.delete_contact);
            builder.setMessage(R.string.comfirm_delete);
            builder.setPositiveButton(R.string.yes, (dialog, which) -> dao.deleteContact(result));
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
            });
            builder.show();
        });
        holder.buttonCall.setOnClickListener(v -> {
            ContactDao dao = mActivity.getDao();
            Contact contact = ContactDB.getContactOrDelete(dao,new UUID(Crypto.from64(holder.contactUUID.getText().toString())));
            if(contact==null)   return;
            mActivity.getService().startCalling(contact);
        });
        holder.buttonAcceptCall.setOnClickListener(v -> {
            ContactDao dao = mActivity.getDao();
            Contact contact = ContactDB.getContactOrDelete(dao,new UUID(Crypto.from64(holder.contactUUID.getText().toString())));
            if(contact==null)   return;
            mActivity.getService().acceptCall(contact);
        });
        holder.buttonRejectCall.setOnClickListener(v -> {
            ContactDao dao = mActivity.getDao();
            Contact contact = ContactDB.getContactOrDelete(dao,new UUID(Crypto.from64(holder.contactUUID.getText().toString())));
            if(contact==null)   return;
            mActivity.getService().reject(contact);
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
        holder.setDefaultVisibility();

        switch (contact.status){
            case READY:
                holder.contactFingerPrint.setText(Crypto.bytesToHex(contact.pkSHA256(),":"));
                if(!locked) holder.buttonCall.setVisibility(View.VISIBLE);
                break;
            case CONFIRM_WAIT:
                //holder.contactStatus.setText(R.string.wait_for_confirm);
                holder.contactFingerPrint.setText(Crypto.bytesToHex(contact.pkSHA256(),":"));
                holder.buttonAccept.setVisibility(View.VISIBLE);
                holder.buttonReject.setVisibility(View.VISIBLE);
                break;
            case PAIR_SENT:
            case PAIR_RCVD:
                holder.contactStatus.setText(R.string.wait_for_response);
                holder.contactStatus.setVisibility(View.VISIBLE);
                holder.buttonRetry.setVisibility(View.VISIBLE);
                holder.contactFingerPrint.setVisibility(View.GONE);
                break;
        }
        if(contact.otherPkt!=null){
            if(contact.invoke>System.currentTimeMillis()-1000) {
                holder.layoutIncoming.setVisibility(View.VISIBLE);
                if(locked)  holder.buttonAcceptCall.setVisibility(View.GONE);
                holder.buttonCall.setVisibility(View.GONE);
            }
            else{
                holder.contactStatus.setVisibility(View.VISIBLE);
                holder.contactStatus.setText(R.string.missed_call);
            }
        }
        if(mEditable){
            holder.setDefaultVisibility();
            holder.buttonEdit.setVisibility(View.VISIBLE);
            if(!locked) holder.buttonDelete.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return mContactList ==null?0: mContactList.size();
    }

    private List<ContactEntity> mContactList;
    private final MainActivity mActivity;

    public boolean isEditable() {
        return mEditable;
    }

    private boolean mEditable;

    public void setLocked(boolean locked) {
        this.locked = locked;
        notifyDataSetChanged();
    }
    public void setEditable(boolean editable) {
        this.mEditable = editable;
        notifyDataSetChanged();
    }

    private boolean locked;
    public ContactAdapter(MainActivity activity, List<ContactEntity> contacts, boolean editable){
        mActivity = activity;
        setContactList(contacts);
        mEditable = editable;
        locked = false;
    }
    public ContactAdapter(MainActivity activity, List<ContactEntity> contacts){
        this(activity,contacts,false);
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
        ImageButton buttonEdit;
        ImageButton buttonDelete;
        ImageButton buttonAcceptCall;
        ImageButton buttonRejectCall;
        View layoutIncoming;

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
            buttonEdit = view.findViewById(R.id.button_edit_contact);
            buttonDelete = view.findViewById(R.id.button_delete_contact);
            buttonAcceptCall = view.findViewById(R.id.button_accept_call);
            buttonRejectCall = view.findViewById(R.id.button_reject_call);
            layoutIncoming = view.findViewById(R.id.layout_incoming);
        }
        public void setDefaultVisibility(){
            contactFingerPrint.setVisibility(View.VISIBLE);
            contactStatus.setVisibility(View.GONE);
            buttonRetry.setVisibility(View.GONE);
            buttonAccept.setVisibility(View.GONE);
            buttonReject.setVisibility(View.GONE);
            buttonCall.setVisibility(View.GONE);
            buttonEdit.setVisibility(View.GONE);
            buttonDelete.setVisibility(View.GONE);
            layoutIncoming.setVisibility(View.GONE);
        }


    }
}
