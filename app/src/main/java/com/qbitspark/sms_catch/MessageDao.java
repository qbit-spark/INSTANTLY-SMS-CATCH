package com.qbitspark.sms_catch;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageDao {
    @Insert
    long insert(MessageData messageData);

    @Query("SELECT * FROM messages WHERE syncStatus = 0")
    List<MessageData> getUnsyncedMessages();

    @Query("UPDATE messages SET syncStatus = 1 WHERE id = :id")
    void markAsSynced(long id);

    @Query("DELETE FROM messages WHERE id = :id")
    void deleteMessage(long id);
}