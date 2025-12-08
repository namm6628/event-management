package com.example.myapplication.common.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TicketType implements Serializable {

    // id document trong subcollection ticketTypes
    private String id;

    private String name;   // VIP, Standard, sinh viên, học sinh...
    private double price;
    private int quota;     // tổng số vé
    private int sold;      // đã bán

    // 🔹 ƯU ĐÃI
    private Double earlyBirdPrice;      // giá đặt sớm
    private Timestamp earlyBirdUntil;   // (optional) đến thời điểm này là hết ưu đãi sớm
    private Double memberPrice;         // giá cho thành viên
    private Integer earlyBirdLimit;     // 🔥 số vé tối đa được hưởng early-bird (VD: 10)

    // Chỉ dùng trong app (UI chọn vé, không lưu Firestore)
    @Exclude
    private int selectedQuantity = 0;

    // ====== DANH SÁCH GHẾ ĐÃ CHỌN CHO LOẠI VÉ NÀY (CHỈ DÙNG TRONG APP) ======
    @Exclude
    private List<String> selectedSeatIds = new ArrayList<>();

    public TicketType() {
    }

    public TicketType(String id, String name, double price, int quota, int sold) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.quota = quota;
        this.sold = sold;
    }

    public TicketType(String name, double price, int quota, int sold) {
        this.name = name;
        this.price = price;
        this.quota = quota;
        this.sold = sold;
    }

    // ===== Getters & Setters =====
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getQuota() { return quota; }
    public void setQuota(int quota) { this.quota = quota; }

    public int getSold() { return sold; }
    public void setSold(int sold) { this.sold = sold; }

    public Double getEarlyBirdPrice() { return earlyBirdPrice; }
    public void setEarlyBirdPrice(Double earlyBirdPrice) { this.earlyBirdPrice = earlyBirdPrice; }

    public Timestamp getEarlyBirdUntil() { return earlyBirdUntil; }
    public void setEarlyBirdUntil(Timestamp earlyBirdUntil) { this.earlyBirdUntil = earlyBirdUntil; }

    public Double getMemberPrice() { return memberPrice; }
    public void setMemberPrice(Double memberPrice) { this.memberPrice = memberPrice; }

    public Integer getEarlyBirdLimit() { return earlyBirdLimit; }
    public void setEarlyBirdLimit(Integer earlyBirdLimit) { this.earlyBirdLimit = earlyBirdLimit; }

    @Exclude
    public int getSelectedQuantity() { return selectedQuantity; }

    @Exclude
    public void setSelectedQuantity(int selectedQuantity) {
        this.selectedQuantity = selectedQuantity;
    }

    @Exclude
    public List<String> getSelectedSeatIds() { return selectedSeatIds; }

    @Exclude
    public void setSelectedSeatIds(List<String> selectedSeatIds) {
        this.selectedSeatIds = selectedSeatIds;
    }

    @Exclude
    public int getRemainingQuota() {
        return quota - sold;
    }

    @Exclude
    public boolean isSoldOut() {
        return getRemainingQuota() <= 0;
    }

    // ===== Helper: tính giá đang áp dụng cho attendee =====
    @Exclude
    public double getEffectivePrice(boolean isMember) {
        double base = price; // giá gốc
        Timestamp now = Timestamp.now();

        boolean stillInTime = true;
        if (earlyBirdUntil != null) {
            stillInTime = now.compareTo(earlyBirdUntil) < 0;
        }

        Integer limit = earlyBirdLimit;
        boolean stillInQuota = true;
        if (limit != null && limit > 0) {
            stillInQuota = sold < limit;
        }

        // 1. Ưu đãi đặt sớm
        if (earlyBirdPrice != null && earlyBirdPrice > 0
                && stillInTime
                && stillInQuota) {
            return earlyBirdPrice;
        }

        // 2. Giá thành viên
        if (isMember && memberPrice != null && memberPrice > 0) {
            return memberPrice;
        }

        // 3. Không ưu đãi
        return base;
    }

    @Exclude
    public String getPromoLabel(boolean isMember) {
        Timestamp now = Timestamp.now();

        boolean stillInTime = true;
        if (earlyBirdUntil != null) {
            stillInTime = now.compareTo(earlyBirdUntil) < 0;
        }

        Integer limit = earlyBirdLimit;
        boolean stillInQuota = true;
        if (limit != null && limit > 0) {
            stillInQuota = sold < limit;
        }

        if (earlyBirdPrice != null && earlyBirdPrice > 0
                && stillInTime
                && stillInQuota) {
            return "Ưu đãi đặt sớm";
        }

        if (isMember && memberPrice != null && memberPrice > 0) {
            return "Giá thành viên";
        }

        return null;
    }
}
