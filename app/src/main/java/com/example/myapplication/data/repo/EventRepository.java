package com.example.myapplication.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.local.EventDao;
import com.example.myapplication.data.local.EventEntity;
import com.example.myapplication.data.remote.EventRemoteDataSource;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Tasks;

public class EventRepository {

    @Nullable
    private final EventDao local;
    @NonNull
    private final EventRemoteDataSource remote;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public EventRepository(@Nullable EventDao local, @NonNull EventRemoteDataSource remote) {
        this.local = local;
        this.remote = remote;
    }

    public EventRepository(@NonNull EventRemoteDataSource remote) {
        this(null, remote);
    }

    public LiveData<List<EventEntity>> getAllLocal() {
        if (local == null) return null;
        return local.getAll();
    }

    // --- Đồng bộ dữ liệu từ Firestore về local ---
    public void refreshAll(@NonNull Runnable onDone,
                           @NonNull EventRemoteDataSource.Failure onError) {
        remote.fetchAll(events -> executor.execute(() -> {
            if (local != null) {
                java.util.List<EventEntity> list = new java.util.ArrayList<>();
                for (com.example.myapplication.common.model.Event e : events) {
                    EventEntity entity = new EventEntity();

                    if (e.getId() != null) entity.setId(e.getId());
                    else entity.setId(java.util.UUID.randomUUID().toString());

                    entity.setTitle(e.getTitle());
                    entity.setLocation(e.getLocation());
                    entity.setCategory(e.getCategory());
                    entity.setThumbnail(e.getThumbnail());

                    Long millis = null;
                    if (e.getStartTime() != null && e.getStartTime().toDate() != null) {
                        millis = e.getStartTime().toDate().getTime();
                    }
                    entity.setStartTime(millis);


                    entity.setPrice(e.getPrice() == null ? 0.0 : e.getPrice());
                    entity.setAvailableSeats(e.getAvailableSeats() == null ? 0 : e.getAvailableSeats());
                    entity.setTotalSeats(e.getTotalSeats() == null ? 0 : e.getTotalSeats());

                    list.add(entity);
                }
                local.clear();
                local.upsertAll(list);
            }
            onDone.run();
        }), onError);


    }
    public void fetchAllDirect(@NonNull EventRemoteDataSource.Success<java.util.List<Event>> onSuccess,
                               @NonNull EventRemoteDataSource.Failure onError) {
        remote.fetchAll(onSuccess, onError);
    }

    public Task<QuerySnapshot> loadFirstPage(String category, int limit) {
        return remote.loadFirstPage(category, limit);
    }

    public Task<QuerySnapshot> loadNextPage(String category, int limit, DocumentSnapshot lastVisible) {
        return remote.loadNextPage(category, limit, lastVisible);
    }

    public void clearLocal() {
        if (local != null) executor.execute(local::clear);
    }
    public void upsertFromRemote(@NonNull List<com.example.myapplication.common.model.Event> events) {
        if (local == null) return;
        executor.execute(() -> {
            List<EventEntity> list = new ArrayList<>();
            for (com.example.myapplication.common.model.Event e : events) {
                EventEntity entity = new EventEntity();
                entity.setId(e.getId() != null ? e.getId() : java.util.UUID.randomUUID().toString());
                entity.setTitle(e.getTitle());
                entity.setLocation(e.getLocation());
                entity.setCategory(e.getCategory());
                entity.setThumbnail(e.getThumbnail());
                Long millis = null;
                if (e.getStartTime() != null && e.getStartTime().toDate() != null) {
                    millis = e.getStartTime().toDate().getTime();
                }
                entity.setStartTime(millis);
                entity.setPrice(e.getPrice() == null ? 0.0 : e.getPrice());
                entity.setAvailableSeats(e.getAvailableSeats() == null ? 0 : e.getAvailableSeats());
                entity.setTotalSeats(e.getTotalSeats() == null ? 0 : e.getTotalSeats());
                list.add(entity);
            }
            local.upsertAll(list);
        });
    }

    public Task<Void> addCollaborator(String eventId, String email, String role) {
        return remote.addCollaborator(eventId, email, role);
    }

    public Task<Void> removeCollaborator(String eventId, String email) {
        return remote.removeCollaborator(eventId, email);
    }

    public Task<QuerySnapshot> getCollaborators(String eventId) {
        return remote.getCollaborators(eventId);
    }

    public Task<Boolean> canUserCheckin(String eventId, String email, String currentUid) {
        return remote.canUserCheckin(eventId, email, currentUid);
    }

}
