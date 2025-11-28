package com.example.myapplication.common.model;

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

    // Chỉ dùng trong app (UI chọn vé, không lưu Firestore)
    @Exclude
    private int selectedQuantity = 0;

    // ====== DANH SÁCH GHẾ ĐÃ CHỌN CHO LOẠI VÉ NÀY (CHỈ DÙNG TRONG APP) ======
    @Exclude
    private List<String> selectedSeatIds = new ArrayList<>();

    public TicketType() {
        // Firestore bắt buộc phải có constructor rỗng public
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuota() {
        return quota;
    }

    public void setQuota(int quota) {
        this.quota = quota;
    }

    public int getSold() {
        return sold;
    }

    public void setSold(int sold) {
        this.sold = sold;
    }

    // ==== Field chỉ dùng trong app (không lưu Firestore) ====

    @Exclude
    public int getSelectedQuantity() {
        return selectedQuantity;
    }

    @Exclude
    public void setSelectedQuantity(int selectedQuantity) {
        this.selectedQuantity = selectedQuantity;
    }

    // ------ GHẾ ĐÃ CHỌN CHO LOẠI VÉ NÀY ------
    @Exclude
    public List<String> getSelectedSeatIds() {
        return selectedSeatIds;
    }

    @Exclude
    public void setSelectedSeatIds(List<String> selectedSeatIds) {
        this.selectedSeatIds = selectedSeatIds;
    }

    // tiện cho check còn vé không
    @Exclude
    public int getRemainingQuota() {
        return quota - sold;
    }

    // tiện cho check hết vé chưa
    @Exclude
    public boolean isSoldOut() {
        return getRemainingQuota() <= 0;
    }

}
