package team.genesis.android.activevoip.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.data.Contact;
import team.genesis.data.UUID;

@Database(entities = Contact.class,version = 1)
public abstract class ContactDB extends RoomDatabase {
    public abstract ContactDao getDao();
    public static ContactEntity[] findContactByUUID(ContactDao dao,UUID uuid){
        return dao.findContactByHash(Crypto.sha256(uuid.getBytes()));
    }
    public static void deleteContactByUUID(ContactDao dao,UUID uuid){
        dao.deleteContactByHash(Crypto.sha256(uuid.getBytes()));
    }
}