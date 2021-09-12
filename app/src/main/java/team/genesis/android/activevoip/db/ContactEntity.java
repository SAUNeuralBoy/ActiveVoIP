package team.genesis.android.activevoip.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.data.Contact;

@Entity
public class ContactEntity {
    @PrimaryKey
    byte[] uuidHash;
    byte[] encryptedData;
    public ContactEntity(Contact contact){
        uuidHash = Crypto.md5(contact.uuid.getBytes());

    }
}
