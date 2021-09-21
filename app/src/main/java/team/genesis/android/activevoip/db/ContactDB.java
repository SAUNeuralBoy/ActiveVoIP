package team.genesis.android.activevoip.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.data.Contact;
import team.genesis.data.UUID;

@Database(entities = ContactEntity.class,version = 1,exportSchema = false)
public abstract class ContactDB extends RoomDatabase {
    public abstract ContactDao getDao();
    public static ContactEntity findContactByUUID(ContactDao dao,UUID uuid){
        ContactEntity[] result = dao.findContactByHash(Crypto.sha256(uuid.getBytes()));
        if(result==null||result.length<1)   return null;
        return result[0];
    }
    public static Contact getContactOrDelete(ContactDao dao,ContactEntity entity){
        try {
            return entity.getContact();
        } catch (Crypto.DecryptException e) {
            dao.deleteContact(entity);
            return null;
        }
    }
    public static Contact getContactOrDelete(ContactDao dao,UUID uuid){
        ContactEntity entity = findContactByUUID(dao,uuid);
        if(entity==null)    return null;
        return getContactOrDelete(dao,entity);
    }
}
