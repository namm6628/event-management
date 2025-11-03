package com.example.myapplication.common;

import android.content.Context;

import com.example.myapplication.data.local.AppDatabase;
import com.example.myapplication.data.remote.EventRemoteDataSource;
import com.example.myapplication.data.repo.EventRepository;

public final class ServiceLocator {
    private static AppDatabase db;

    public static synchronized EventRepository eventRepo(Context ctx){
        if (db == null) db = AppDatabase.create(ctx.getApplicationContext());
        return new EventRepository(db.eventDao(), new EventRemoteDataSource());
    }
}
