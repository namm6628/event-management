package com.example.myapplication.common.model;

import java.io.Serializable;

/**
 * Model duy nhất cho Event, có getter/setter.
 * - Tương thích UI mới (getTitle(), getLocation(), getPrice() Integer, getStartTime() Long, ...)
 * - Có thêm constructor cũ (id, title, date, venue, coverUrl, price, category) để không vỡ các chỗ cũ.
 */
public class Event implements Serializable {

    // ====== Trường "chuẩn hóa" cho app ======
    private String id;
    private String title;
    private String location;        // thay cho "venue"
    private Integer price;          // dạng số (VND), thay cho chuỗi "price"
    private Long startTime;         // epoch millis (nếu có)
    private Integer availableSeats;
    private Integer totalSeats;
    private String thumbnail;       // thay cho "coverUrl"
    private String category;

    // ====== Trường hỗ trợ runtime (giữ theo code cũ) ======
    public transient int matchScore = 0;
    public transient long whenMillis = Long.MAX_VALUE;

    public Event() {
        // bắt buộc cho Firestore/Room
    }

    /**
     * Constructor "tương thích ngược" với code cũ:
     * public String id, title, date, venue, coverUrl, price, category
     * - date: cố gắng chuyển sang millis nếu về sau bạn cần. Hiện đặt null để an toàn.
     * - price: cố gắng parse số từ chuỗi; nếu không parse được -> null.
     */
    public Event(String id, String title, String date, String venue,
                 String coverUrl, String price, String category) {
        this.id = id;
        this.title = title;
        this.location = venue;
        this.thumbnail = coverUrl;
        this.category = category;

        // Parse price (chuỗi -> số). Ví dụ "300000" hoặc "300.000đ"
        if (price != null) {
            try {
                String digits = price.replaceAll("[^0-9]", "");
                this.price = digits.isEmpty() ? null : Integer.parseInt(digits);
            } catch (Exception ignored) {
                this.price = null;
            }
        }

        // date cũ dạng chuỗi (vd: "20/11/2025 19:00") -> để null tạm thời để tránh sai lệch format
        // Bạn có thể viết parser nếu muốn, ví dụ SimpleDateFormat...
        this.startTime = null;
    }

    // ====== Getter / Setter chuẩn ======

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    /** location = nơi diễn ra (thay cho "venue"). */
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    /** Giá (VND) dạng số. */
    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }

    /** Thời gian bắt đầu (epoch millis), nếu có. */
    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }

    public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }

    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }

    /** Ảnh bìa (URL), thay cho "coverUrl" cũ. */
    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
