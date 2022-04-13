package team.genesis.android.activevoip.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.Gson;

import java.time.Instant;
import java.util.Objects;

import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.data.Contact;

@Entity
public class ContactEntity {
    @PrimaryKey
    @NonNull
    byte[] uuidHash;
    byte[] encryptedData;
    long timeStamp;
    public ContactEntity(Contact contact){
        uuidHash = Objects.requireNonNull(Crypto.sha256(contact.uuid.getBytes()));
        encryptedData = Crypto.encryptWithMasterKey(new Gson().toJson(contact).getBytes());
        timeStamp = System.currentTimeMillis();
    }
    public ContactEntity(){
        uuidHash = new byte[32];
    }
    public Contact getContact() throws Crypto.DecryptException {
        return new Gson().fromJson(new String(Crypto.decryptWithMasterKey(encryptedData)),Contact.class);
    }
    public long getTimeStamp(){
        return timeStamp;
    }
}
