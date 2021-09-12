package team.genesis.android.activevoip.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;

import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.data.Contact;

@Entity
public class ContactEntity {
    @PrimaryKey
    byte[] uuidHash;
    byte[] encryptedData;
    public ContactEntity(Contact contact){
        uuidHash = Crypto.md5(contact.uuid.getBytes());
        encryptedData = Crypto.encryptWithMasterKey(new Gson().toJson(contact).getBytes());
    }
    public Contact getContact() throws Crypto.DecryptException {
        return new Gson().fromJson(new String(Crypto.decryptWithMasterKey(encryptedData)),Contact.class);
    }
}
