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
import androidx.annotation.NonNull;

import com.example.myapplication.common.model.Event;
import com.google.android.gms.tasks.Task;
// [THÊM MỚI] - Imports
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar; // [THÊM MỚI]
import java.util.Date; // [THÊM MỚI]
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

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

    /**
     * [THÊM MỚI] - Load "Sự kiện xu hướng"
     * Logic: Lấy các sự kiện sắp diễn ra sớm nhất
     */
    public Task<QuerySnapshot> loadTrendingEvents(int limit) {
        Date now = new Date(); // Lấy thời gian hiện tại

        Query q = db.collection("events")
                .whereGreaterThanOrEqualTo("startTime", new Timestamp(now)) // Chỉ lấy sự kiện tương lai
                .orderBy("startTime", Query.Direction.ASCENDING) // Sắp xếp cái nào diễn ra sớm nhất
                .limit(limit);
        return q.get();
    }

    /**
     * [THÊM MỚI] - Load "Dành cho bạn"
     * Logic: Lấy sự kiện tương lai có nhiều chỗ trống nhất
     * ⚠️ Yêu cầu COMPOSITE INDEX (Xem cảnh báo bên dưới)
     */
    public Task<QuerySnapshot> loadForYouEvents(int limit) {
        Date now = new Date(); // Lấy thời gian hiện tại

        Query q = db.collection("events")
                .whereGreaterThanOrEqualTo("startTime", new Timestamp(now)) // Lọc sự kiện tương lai
                .orderBy("startTime", Query.Direction.ASCENDING) // Sắp xếp phụ (bắt buộc)
                .orderBy("availableSeats", Query.Direction.DESCENDING) // Sắp xếp chính
                .limit(limit);

        return q.get();
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

    /**
     * [THÊM MỚI] - Load sự kiện cho "Cuối tuần này"
     * Tự động truy vấn các sự kiện từ 00:00 T.Sáu tới 23:59 T.Nhật
     */
    public Task<QuerySnapshot> loadEventsForWeekend(int limit) {
        // 1. Lấy mốc thời gian T.Sáu và T.Nhật
        Date[] weekend = getWeekendDates();
        Date fridayStart = weekend[0];
        Date sundayEnd = weekend[1];

        // 2. Tạo truy vấn (Firestore yêu cầu 'startTime' là đối tượng Timestamp hoặc Date)
        Query q = db.collection("events")
                .whereGreaterThanOrEqualTo("startTime", new Timestamp(fridayStart))
                .whereLessThanOrEqualTo("startTime", new Timestamp(sundayEnd))
                .orderBy("startTime", Query.Direction.ASCENDING) // Bắt buộc orderBy khi dùng range filter // Bắt buộc orderBy khi dùng range filter
                .limit(limit);

        return q.get();
    }

    /**
     * [THÊM MỚI] - Hàm trợ giúp lấy mốc T.Sáu 00:00 và T.Nhật 23:59
     */
    private Date[] getWeekendDates() {
        Calendar calendar = Calendar.getInstance();

        // 1. Tìm ngày T.Sáu tới
        // Nếu hôm nay là T.Bảy hoặc T.Nhật, nó sẽ tìm T.Sáu tuần sau
        if (calendar.get(Calendar.DAY_OF_WEEK) > Calendar.FRIDAY) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
        }
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);

        // Set T.Sáu 00:00:00
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date fridayStart = calendar.getTime();

        // 2. Tìm ngày T.Nhật (T.Sáu + 2 ngày)
        calendar.add(Calendar.DAY_OF_MONTH, 2);

        // Set T.Nhật 23:59:59
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        Date sundayEnd = calendar.getTime();

        return new Date[]{fridayStart, sundayEnd};
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
