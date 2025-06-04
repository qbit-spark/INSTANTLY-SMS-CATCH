package com.qbitspark.sms_catch;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {MessageData.class}, version = 2)  // Increment version for schema change
public abstract class MessageDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "message_db";
    private static MessageDatabase instance;

    public abstract MessageDao messageDao();

    public static synchronized MessageDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            MessageDatabase.class,
                            DATABASE_NAME)
                    .fallbackToDestructiveMigration()  // Add this for schema change
                    .build();
        }
        return instance;
    }
}