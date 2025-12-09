package com.example.myapplication.data.remote;

import androidx.annotation.NonNull;

import com.example.myapplication.common.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class EventRemoteDataSource {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Success<T> { void accept(T t); }
    public interface Failure { void accept(Exception e); }

    // ================== PHÂN TRANG CHÍNH ==================

    public Task<QuerySnapshot> loadFirstPage(String category, int limit) {
        Query q = db.collection("events").orderBy("startTime");
        if (category != null && !"Tất cả".equalsIgnoreCase(category)) {
            q = q.whereEqualTo("category", category);
        }
        return q.limit(limit).get();
    }

    public Task<QuerySnapshot> loadNextPage(String category, int limit, DocumentSnapshot lastVisible) {
        Query q = db.collection("events").orderBy("startTime");
        if (category != null && !"Tất cả".equalsIgnoreCase(category)) {
            q = q.whereEqualTo("category", category);
        }
        if (lastVisible != null) {
            q = q.startAfter(lastVisible);
        }
        return q.limit(limit).get();
    }

    // ================== TRENDING ==================

    public Task<QuerySnapshot> loadTrendingEvents(int limit) {
        Date now = new Date();

        Query q = db.collection("events")
                .whereGreaterThanOrEqualTo("startTime", new Timestamp(now))
                .orderBy("startTime", Query.Direction.ASCENDING)
                .limit(limit);

        return q.get();
    }

    // ================== FOR YOU (AI) ==================

    public Task<QuerySnapshot> loadForYouEvents(int limit, String userInterest) {
        Query q = db.collection("events");

        if (userInterest != null && !userInterest.isEmpty()) {
            q = q.whereEqualTo("category", userInterest);
        } else {
            q = q.orderBy("availableSeats", Query.Direction.DESCENDING);
        }

        return q.limit(limit).get();
    }

    // ================== CUỐI TUẦN ==================

    public Task<QuerySnapshot> loadEventsForWeekend(int limit) {
        Date[] weekend = getWeekendDates();
        Date fridayStart = weekend[0];
        Date sundayEnd   = weekend[1];

        Query q = db.collection("events")
                .whereGreaterThanOrEqualTo("startTime", new Timestamp(fridayStart))
                .whereLessThanOrEqualTo("startTime", new Timestamp(sundayEnd))
                .orderBy("startTime", Query.Direction.ASCENDING)
                .limit(limit);

        return q.get();
    }

    private Date[] getWeekendDates() {
        Calendar calendar = Calendar.getInstance();

        if (calendar.get(Calendar.DAY_OF_WEEK) > Calendar.FRIDAY) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
        }
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date fridayStart = calendar.getTime();

        calendar.add(Calendar.DAY_OF_MONTH, 2);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        Date sundayEnd = calendar.getTime();

        return new Date[]{fridayStart, sundayEnd};
    }

    // ================== VIDEO EVENTS ==================

    public Task<QuerySnapshot> loadVideoEvents(int limit) {
        Query q = db.collection("events")
                .whereEqualTo("hasVideo", true)
                .orderBy("startTime", Query.Direction.ASCENDING)
                .limit(limit);

        return q.get();
    }

    // ================== HÀM CŨ HỖ TRỢ REPO ==================

    public void fetchAll(@NonNull Success<List<Event>> onSuccess,
                         @NonNull Failure onError) {
        db.collection("events")
                .orderBy("startTime")
                .get()
                .addOnSuccessListener(snap -> onSuccess.accept(map(snap)))
                .addOnFailureListener(onError::accept);
    }

    public void fetchByCategory(@NonNull String category,
                                @NonNull Success<List<Event>> onSuccess,
                                @NonNull Failure onError) {
        Query q = db.collection("events").orderBy("startTime");
        if (category != null && !"Tất cả".equalsIgnoreCase(category)) {
            q = q.whereEqualTo("category", category);
        }
        q.get()
                .addOnSuccessListener(snap -> onSuccess.accept(map(snap)))
                .addOnFailureListener(onError::accept);
    }

    // ================== UTIL: MAP & FILTER ==================

    public static List<Event> map(QuerySnapshot snap) {
        List<Event> list = new ArrayList<>();
        if (snap == null) return list;
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Event e = doc.toObject(Event.class);
            if (e != null) {
                e.setId(doc.getId());
                list.add(e);
            }
        }
        return list;
    }

    public static List<Event> filterClient(List<Event> events, String query) {
        if (events == null || events.isEmpty()) return new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return events;

        String q = query.toLowerCase(Locale.getDefault());
        return events.stream()
                .filter(e ->
                        (e.getTitle() != null &&
                                e.getTitle().toLowerCase(Locale.getDefault()).contains(q)) ||
                                (e.getLocation() != null &&
                                        e.getLocation().toLowerCase(Locale.getDefault()).contains(q))
                )
                .collect(Collectors.toList());
    }

    // ================== COLLABORATORS SUBCOLLECTION ==================

    // KHÔNG normalize nữa – dùng docId = email gốc
    public Task<Void> addCollaborator(String eventId, String email, String role) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("role", role);
        data.put("createdAt", FieldValue.serverTimestamp());

        return db.collection("events")
                .document(eventId)
                .collection("collaborators")
                .document(email)     // docId = email
                .set(data, SetOptions.merge());
    }

    public Task<Void> removeCollaborator(String eventId, String email) {
        return db.collection("events")
                .document(eventId)
                .collection("collaborators")
                .document(email)
                .delete();
    }

    public Task<QuerySnapshot> getCollaborators(String eventId) {
        return db.collection("events")
                .document(eventId)
                .collection("collaborators")
                .get();
    }

    public Task<Boolean> canUserCheckin(String eventId, String email, String currentUid) {
        Task<DocumentSnapshot> eventTask = db.collection("events")
                .document(eventId)
                .get();

        Task<DocumentSnapshot> collabTask = db.collection("events")
                .document(eventId)
                .collection("collaborators")
                .document(email)
                .get();

        return Tasks.whenAllSuccess(eventTask, collabTask)
                .continueWith(task -> {
                    DocumentSnapshot eventSnap = eventTask.getResult();
                    DocumentSnapshot collabSnap = collabTask.getResult();

                    boolean isOwner = false;
                    if (eventSnap.exists()) {
                        String ownerId = eventSnap.getString("ownerId");
                        isOwner = ownerId != null && ownerId.equals(currentUid);
                    }

                    boolean isCollab = false;
                    if (collabSnap.exists()) {
                        String role = collabSnap.getString("role");
                        isCollab = "checkin".equals(role);
                    }

                    return isOwner || isCollab;
                });
    }
}
