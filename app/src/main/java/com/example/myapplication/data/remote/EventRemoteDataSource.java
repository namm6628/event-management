package com.example.myapplication.data.remote;

import androidx.annotation.NonNull;

import com.example.myapplication.common.model.Event;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * EventRemoteDataSource:
 * - Quản lý lấy dữ liệu từ Firestore (collection "events").
 * - Hỗ trợ phân trang, lọc, search client-side.
 * - Có thêm các API cho Trending / ForYou / Weekend / Video.
 */
public class EventRemoteDataSource {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Functional callbacks dùng cho code cũ (nếu còn dùng) */
    public interface Success<T> { void accept(T t); }
    public interface Failure { void accept(Exception e); }

    // ================== PHÂN TRANG CHÍNH ==================

    /** Trang đầu cho list chính (có filter category) */
    public Task<QuerySnapshot> loadFirstPage(String category, int limit) {
        Query q = db.collection("events").orderBy("startTime");
        if (category != null && !"Tất cả".equalsIgnoreCase(category)) {
            q = q.whereEqualTo("category", category);
        }
        return q.limit(limit).get();
    }

    /** Trang tiếp theo */
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

    /**
     * Sự kiện xu hướng:
     * lấy các event tương lai, gần hiện tại nhất.
     */
    public Task<QuerySnapshot> loadTrendingEvents(int limit) {
        Date now = new Date();

        Query q = db.collection("events")
                .whereGreaterThanOrEqualTo("startTime", new Timestamp(now))
                .orderBy("startTime", Query.Direction.ASCENDING)
                .limit(limit);

        return q.get();
    }

    // ================== FOR YOU (AI) ==================

    /**
     * Dành cho bạn:
     * - Nếu biết userInterest -> lọc theo category đó.
     * - Nếu không -> gợi ý các sự kiện tương lai có nhiều chỗ trống (availableSeats) nhất.
     *
     * ⚠️ Nhiều khả năng Firestore sẽ yêu cầu composite index cho:
     *   startTime (ASC) + availableSeats (DESC) [+ category]
     */
//    public Task<QuerySnapshot> loadForYouEvents(int limit, String userInterest) {
//        Date now = new Date();
//
//        Query q = db.collection("events")
//                .whereGreaterThanOrEqualTo("startTime", new Timestamp(now))
//                .orderBy("startTime", Query.Direction.ASCENDING);
//
//        if (userInterest != null && !userInterest.isEmpty()) {
//            // Người dùng đã có “sở thích” -> lọc category
//            q = q.whereEqualTo("category", userInterest)
//                    .orderBy("availableSeats", Query.Direction.DESCENDING);
//        } else {
//            // Người mới -> ưu tiên sự kiện còn nhiều chỗ
//            q = q.orderBy("availableSeats", Query.Direction.DESCENDING);
//        }
//
//        return q.limit(limit).get();
//    }
    public Task<QuerySnapshot> loadForYouEvents(int limit, String userInterest) {
        // [SỬA TẠM] - Bỏ hết điều kiện thời gian, chỉ lọc theo tên category
        Query q = db.collection("events");

        if (userInterest != null && !userInterest.isEmpty()) {
            q = q.whereEqualTo("category", userInterest);
        } else {
            q = q.orderBy("availableSeats", Query.Direction.DESCENDING);
        }

        return q.limit(limit).get();
    }

    // ================== CUỐI TUẦN ==================

    /**
     * Sự kiện cuối tuần:
     * từ 00:00 Thứ Sáu đến 23:59 Chủ Nhật gần nhất.
     */
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

    /** Tính mốc T.Sáu 00:00 và T.Nhật 23:59 cho “cuối tuần này” */
    private Date[] getWeekendDates() {
        Calendar calendar = Calendar.getInstance();

        // Nếu hôm nay sau Thứ Sáu -> nhảy sang tuần sau
        if (calendar.get(Calendar.DAY_OF_WEEK) > Calendar.FRIDAY) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
        }
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);

        // Thứ Sáu 00:00:00
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date fridayStart = calendar.getTime();

        // Chủ Nhật (Thứ Sáu + 2 ngày)
        calendar.add(Calendar.DAY_OF_MONTH, 2);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        Date sundayEnd = calendar.getTime();

        return new Date[]{fridayStart, sundayEnd};
    }

    // ================== VIDEO EVENTS ==================

    /**
     * Sự kiện có video giới thiệu:
     * - `hasVideo == true`
     * - Sự kiện tương lai
     */
    public Task<QuerySnapshot> loadVideoEvents(int limit) {
        Query q = db.collection("events")
                .whereEqualTo("hasVideo", true)
                .orderBy("startTime", Query.Direction.ASCENDING)
                .limit(limit);

        return q.get();
    }


    // ================== HÀM CŨ HỖ TRỢ REPO ==================

    /** Lấy tất cả events (không phân trang) */
    public void fetchAll(@NonNull Success<List<Event>> onSuccess,
                         @NonNull Failure onError) {
        db.collection("events")
                .orderBy("startTime")
                .get()
                .addOnSuccessListener(snap -> onSuccess.accept(map(snap)))
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
                .addOnSuccessListener(snap -> onSuccess.accept(map(snap)))
                .addOnFailureListener(onError::accept);
    }

    // ================== UTIL: MAP & FILTER ==================

    /** Map Firestore snapshot -> List<Event> */
    public static List<Event> map(QuerySnapshot snap) {
        List<Event> list = new ArrayList<>();
        if (snap == null) return list;
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Event e = doc.toObject(Event.class);
            if (e != null) {
                e.setId(doc.getId()); // luôn gán documentId
                list.add(e);
            }
        }
        return list;
    }

    /** Lọc client-side theo query text (title hoặc location) */
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
}
