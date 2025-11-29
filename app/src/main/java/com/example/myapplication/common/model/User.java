package com.example.myapplication.common.model;

import com.google.firebase.Timestamp;

public class User {

    private String id;
    private String name;
    private String email;
    private String phone;
    private Timestamp createdAt;

    // 🔥 Thêm membership tier
    // normal = mặc định
    // member = ưu đãi nhẹ
    // vip = ưu đãi mạnh
    private String membershipTier;

    public User() {
        // Firestore cần constructor rỗng
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getMembershipTier() {
        return membershipTier;
    }

    public void setMembershipTier(String membershipTier) {
        this.membershipTier = membershipTier;
    }

    // ===== Helper =====

    public boolean isMember() {
        return membershipTier != null
                && (membershipTier.equalsIgnoreCase("member")
                || membershipTier.equalsIgnoreCase("vip"));
    }

    public boolean isVip() {
        return membershipTier != null
                && membershipTier.equalsIgnoreCase("vip");
    }
}
