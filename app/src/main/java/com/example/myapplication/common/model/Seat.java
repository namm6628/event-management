package com.example.myapplication.common.model;

public class Seat {
    private String id;      // documentId = "A1"
    private String row;     // "A"
    private int number;     // 1
    private String type;    // "VIP", "Standard", ...
    private String status;  // "available", "booked", "hold"
    private long price;     // giá theo ghế

    private String ticketTypeId;  // Thêm ID của loại vé (từ ticketTypes)

    public Seat() {
        // Firestore cần constructor rỗng
    }

    public Seat(String id, String row, int number, String type, String status, long price, String ticketTypeId) {
        this.id = id;
        this.row = row;
        this.number = number;
        this.type = type;
        this.status = status;
        this.price = price;
        this.ticketTypeId = ticketTypeId;
    }

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getRow() { return row; }

    public void setRow(String row) { this.row = row; }

    public int getNumber() { return number; }


    public void setNumber(int number) { this.number = number; }

    public String getType() { return type; }

    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public long getPrice() { return price; }

    public void setPrice(long price) { this.price = price; }

    public String getTicketTypeId() { return ticketTypeId; }
    public void setTicketTypeId(String ticketTypeId) { this.ticketTypeId = ticketTypeId; }


    public String getLabel() {
        String r = row == null ? "" : row;
        return r + number;
    }

}
