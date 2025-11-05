package com.example.myapplication.data.remote;

import androidx.annotation.NonNull;

import com.example.myapplication.common.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * EventRemoteDataSource:
 * - Quản lý lấy dữ liệu từ Firestore (collection "events").
 * - Hỗ trợ phân trang, lọc, tìm kiếm client-side.
 * - Có thêm fetchAll() và fetchByCategory() cho repo cũ.
 */
public class EventRemoteDataSource {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Functional callbacks dùng cho Java 8 lambda */
    public interface Success<T> { void accept(T t); }
    public interface Failure { void accept(Exception e); }

    /** Load trang đầu (phân trang) */
    public Task<QuerySnapshot> loadFirstPage(String category, int limit) {
        Query q = db.collection("events").orderBy("startTime");
        if (category != null && !"Tất cả".equalsIgnoreCase(category)) {
            q = q.whereEqualTo("category", category);
        }
        return q.limit(limit).get();
    }

    /** Load trang tiếp theo (phân trang) */
    public Task<QuerySnapshot> loadNextPage(String category, int limit, DocumentSnapshot lastVisible) {
        Query q = db.collection("events").orderBy("startTime");
        if (category != null && !"Tất cả".equalsIgnoreCase(category)) {
            q = q.whereEqualTo("category", category);
        }
        if (lastVisible != null) q = q.startAfter(lastVisible);
        return q.limit(limit).get();
    }

    /** Lấy tất cả events (không phân trang) */
    public void fetchAll(@NonNull Success<List<Event>> onSuccess,
                         @NonNull Failure onError) {
        db.collection("events")
                .orderBy("startTime")
                .get()
                .addOnSuccessListener(snap -> {
                    List<Event> list = map(snap);
                    onSuccess.accept(list);
                })
                .addOnFailureListener(onError::accept);
    }

    /** Lấy tất cả events theo category (không phân trang) */
    public void fetchByCategory(@NonNull String category,
                                @NonNull Success<List<Event>> onSuccess,
                                @NonNull Failure onError) {
        Query q = db.collection("events").orderBy("startTime");
        if (category != null && !"Tất cả".equalsIgnoreCase(category)) {
            q = q.whereEqualTo("category", category);
        }
        q.get()
                .addOnSuccessListener(snap -> {
                    List<Event> list = map(snap);
                    onSuccess.accept(list);
                })
                .addOnFailureListener(onError::accept);
    }

    /** Chuyển Firestore snapshot -> list<Event> */
    public static List<Event> map(QuerySnapshot snap) {
        List<Event> list = new ArrayList<>();
        if (snap == null) return list;
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Event e = doc.toObject(Event.class);
            if (e != null) {
                e.setId(doc.getId()); // ✅ luôn gán id từ documentId
                list.add(e);
            }
        }
        return list;
    }



    /** Lọc client-side (theo query text) */
    public static List<Event> filterClient(List<Event> events, String query) {
        if (events == null || events.isEmpty()) return new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return events;

        String q = query.toLowerCase(Locale.getDefault());
        return events.stream()
                .filter(e ->
                        (e.getTitle() != null && e.getTitle().toLowerCase(Locale.getDefault()).contains(q)) ||
                                (e.getLocation() != null && e.getLocation().toLowerCase(Locale.getDefault()).contains(q))
                )
                .collect(Collectors.toList());
    }
}
