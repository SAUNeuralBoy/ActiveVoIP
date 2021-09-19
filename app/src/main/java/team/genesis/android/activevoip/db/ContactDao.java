package team.genesis.android.activevoip.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;

import java.util.List;
import java.util.UUID;

@Dao
public interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertContact(ContactEntity... contacts);
    @Query("SELECT * FROM ContactEntity ORDER BY timeStamp DESC")
    List<ContactEntity> getAllContacts();
    @Query("SELECT * FROM ContactEntity ORDER BY timeStamp DESC")
    LiveData<List<ContactEntity>> getAllContactsLive();
    @Query("SELECT * FROM ContactEntity WHERE uuidHash = :sha256")
    ContactEntity[] findContactByHash(byte[] sha256);
    @Delete
    void deleteContact(ContactEntity... contacts);
}
