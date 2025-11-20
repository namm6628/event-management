/* eslint-disable max-len */
"use strict";

const {onCall} = require("firebase-functions/v2/https");
const {onObjectFinalized} = require("firebase-functions/v2/storage");
const admin = require("firebase-admin");

admin.initializeApp();

// Region của project Storage (theo lỗi: asia-southeast1)
const REGION = "asia-southeast1";

/**
 * Gán role "organizer" cho user.
 * App Android gọi callable này với data: { uid: "firebaseUserUid" }.
 */
exports.assignOrganizerRole = onCall(
    {region: REGION},
    async (request) => {
      const uid = request.data.uid;

      if (!uid) {
        throw new Error("Missing uid");
      }

      await admin.auth().setCustomUserClaims(uid, {role: "organizer"});

      return {message: "Organizer role assigned."};
    },
);

exports.importAttendees = functions
  .region("asia-southeast1")
  .https.onCall(async (data, context) => {
    // 1. bắt buộc đăng nhập
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Bạn phải đăng nhập để import attendees."
      );
    }

    const eventId = data.eventId;
    const attendees = Array.isArray(data.attendees) ? data.attendees : [];

    if (!eventId || attendees.length === 0) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Thiếu eventId hoặc danh sách attendees."
      );
    }

    const eventRef = db.collection("events").doc(eventId);
    const eventSnap = await eventRef.get();

    if (!eventSnap.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Sự kiện không tồn tại."
      );
    }

    const eventData = eventSnap.data() || {};
    const totalSeats = eventData.totalSeats || 0;
    let availableSeats = eventData.availableSeats;

    // nếu availableSeats chưa set, mặc định = totalSeats
    if (availableSeats == null) {
      availableSeats = totalSeats;
    }

    const batch = db.batch();
    let createdCount = 0;

    attendees.forEach(a => {
      if (!a || !a.name) return; // bỏ qua dòng không có name

      const attendeeRef = eventRef.collection("attendees").doc();
      const qrCode = `${eventId}|${attendeeRef.id}`;

      const attendeeData = {
        userId: a.userId || null,
        name: a.name,
        email: a.email || null,
        phone: a.phone || null,
        ticketTypeId: a.ticketTypeId || null,
        status: "booked",
        qrCode: qrCode,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        checkedInAt: null
      };

      batch.set(attendeeRef, attendeeData);
      createdCount++;
    });

    if (createdCount === 0) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Không có attendee hợp lệ nào để import."
      );
    }

    // cập nhật availableSeats, không cho âm
    let newAvailable = availableSeats - createdCount;
    if (newAvailable < 0) newAvailable = 0;

    batch.update(eventRef, {
      availableSeats: newAvailable,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    await batch.commit();

    return {
      ok: true,
      imported: createdCount,
      oldAvailableSeats: availableSeats,
      newAvailableSeats: newAvailable
    };
  });



/**
 * Trigger khi có file mới upload lên Firebase Storage.
 * Sau này dùng để import CSV attendee.
 * Hiện tại chỉ log cho chắc chắn deploy được.
 */
exports.importAttendees = onObjectFinalized(
    {region: REGION},
    async (event) => {
      const object = event.data;

      if (!object || !object.name) {
        return;
      }

      const filePath = object.name;

      if (!filePath.endsWith(".csv")) {
        console.log("Not a CSV, skip:", filePath);
        return;
      }

      console.log("CSV file uploaded:", filePath);
    // TODO: parse CSV và import Firestore sau
    },
);
