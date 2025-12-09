package com.example.myapplication.common.model;

import com.google.firebase.Timestamp;

public class User {

    private String id;
    private String name;
    private String email;
    private String phone;
    private Timestamp createdAt;

    private String membershipTier;
    private Boolean isOrganizer;

    public User() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getMembershipTier() { return membershipTier; }
    public void setMembershipTier(String membershipTier) { this.membershipTier = membershipTier; }

    public Boolean getIsOrganizer() { return isOrganizer; }
    public void setIsOrganizer(Boolean organizer) { isOrganizer = organizer; }

    // Helpers
    public boolean isMember() {
        if (membershipTier == null) return false;
        String t = membershipTier.toLowerCase();
        return t.equals("member") || t.equals("vip");
    }

    public boolean isVip() {
        if (membershipTier == null) return false;
        return membershipTier.equalsIgnoreCase("vip");
    }
}
