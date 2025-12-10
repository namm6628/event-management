/* eslint-disable */
"use strict";

const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onObjectFinalized} = require("firebase-functions/v2/storage");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const REGION = "asia-southeast1";

/**
 * Helper: import danh sách attendees vào event, update availableSeats.
 * Dùng chung cho import từ app & từ CSV.
 */
async function importAttendeeList(eventId, attendees) {
  if (!eventId) {
    throw new Error("Missing eventId");
  }
  if (!Array.isArray(attendees) || attendees.length === 0) {
    throw new Error("Empty attendees list");
  }

  const eventRef = db.collection("events").doc(eventId);
  const eventSnap = await eventRef.get();

  if (!eventSnap.exists) {
    throw new Error("Event not found");
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

  attendees.forEach((a) => {
    if (!a || !a.name) {
      return; // bỏ qua dòng không có name
    }

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
      checkedInAt: null,
    };

    batch.set(attendeeRef, attendeeData);
    createdCount++;
  });

  if (createdCount === 0) {
    throw new Error("No valid attendee to import");
  }

  // cập nhật availableSeats, không cho âm
  let newAvailable = availableSeats - createdCount;
  if (newAvailable < 0) {
    newAvailable = 0;
  }

  batch.update(eventRef, {
    availableSeats: newAvailable,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  await batch.commit();

  return {
    ok: true,
    imported: createdCount,
    oldAvailableSeats: availableSeats,
    newAvailableSeats: newAvailable,
  };
}

/**
 * Gán role "organizer" cho user.
 * App Android gọi callable này với data: { uid: "firebaseUserUid" }.
 */
exports.assignOrganizerRole = onCall(
  {region: REGION},
  async (request) => {
    const uid = request.data && request.data.uid;

    if (!uid) {
      throw new HttpsError("invalid-argument", "Missing uid");
    }

    await admin.auth().setCustomUserClaims(uid, {role: "organizer"});

    return {message: "Organizer role assigned."};
  },
);

/**
 * Import attendees trực tiếp từ app (JSON) – cho trường hợp m đẩy danh sách từ app lên.
 * data: { eventId: string, attendees: [ {name, email, phone, ticketTypeId, userId} ] }
 */
exports.importAttendeesFromApp = onCall(
  {region: REGION},
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Bạn phải đăng nhập để import attendees từ app.",
      );
    }

    const data = request.data || {};
    const eventId = data.eventId;
    const attendees = Array.isArray(data.attendees) ? data.attendees : [];

    if (!eventId || attendees.length === 0) {
      throw new HttpsError(
        "invalid-argument",
        "Thiếu eventId hoặc danh sách attendees.",
      );
    }

    try {
      const result = await importAttendeeList(eventId, attendees);
      return result;
    } catch (err) {
      console.error("importAttendeesFromApp error:", err);
      throw new HttpsError("internal", err.message || "Import failed");
    }
  },
);

/**
 * Trigger khi có file CSV upload lên Storage, đường dẫn:
 * imports/{eventId}/anything.csv
 *
 * File CSV đơn giản, header ví dụ:
 * name,email,phone,ticketTypeId,userId
 */
exports.onCsvUploaded = onObjectFinalized(
  {region: REGION},
  async (event) => {
    const object = event.data;
    if (!object || !object.name || !object.bucket) {
      return;
    }

    const filePath = object.name;

    // Chỉ xử lý file trong thư mục imports/ và đuôi .csv
    if (!filePath.endsWith(".csv")) {
      console.log("Not a CSV, skip:", filePath);
      return;
    }
    if (!filePath.startsWith("imports/")) {
      console.log("Not in imports/ folder, skip:", filePath);
      return;
    }

    console.log("CSV file uploaded:", filePath);

    const parts = filePath.split("/");
    // imports/{eventId}/{filename}.csv
    if (parts.length < 3) {
      console.log("CSV path invalid, expected imports/{eventId}/file.csv");
      return;
    }
    const eventId = parts[1];

    try {
      const bucket = admin.storage().bucket(object.bucket);
      const file = bucket.file(filePath);

      const [contents] = await file.download();
      const csvData = contents.toString("utf-8");

      const attendees = parseCsvAttendees(csvData);
      if (!attendees || attendees.length === 0) {
        console.log("No attendees parsed from CSV");
        return;
      }

      const result = await importAttendeeList(eventId, attendees);
      console.log("CSV imported:", result);
    } catch (err) {
      console.error("onCsvUploaded error:", err);
    }
  },
);

/**
 * Parse CSV đơn giản → array attendees.
 * Giả định:
 * - Dòng đầu là header.
 * - Không có dấu phẩy trong từng ô (CSV basic).
 * - Header: name,email,phone,ticketTypeId,userId
 */
function parseCsvAttendees(csvText) {
  if (!csvText) {
    return [];
  }
  const lines = csvText.split(/\r?\n/).filter((l) => l.trim().length > 0);
  if (lines.length <= 1) {
    return [];
  }

  const header = lines[0].split(",").map((h) => h.trim());
  const idxName = header.indexOf("name");
  const idxEmail = header.indexOf("email");
  const idxPhone = header.indexOf("phone");
  const idxTicketTypeId = header.indexOf("ticketTypeId");
  const idxUserId = header.indexOf("userId");

  if (idxName === -1) {
    console.log("CSV missing 'name' column");
    return [];
  }

  const attendees = [];

  for (let i = 1; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) {
      continue;
    }

    const cols = line.split(",");
    const name = (cols[idxName] || "").trim();
    if (!name) {
      continue;
    }

    const attendee = {
      name: name,
      email: idxEmail !== -1 ? (cols[idxEmail] || "").trim() : null,
      phone: idxPhone !== -1 ? (cols[idxPhone] || "").trim() : null,
      ticketTypeId:
        idxTicketTypeId !== -1 ? (cols[idxTicketTypeId] || "").trim() : null,
      userId: idxUserId !== -1 ? (cols[idxUserId] || "").trim() : null,
    };

    attendees.push(attendee);
  }

  return attendees;
}

/**
 * Export attendees ra CSV (Excel mở được).
 * data: { eventId }
 * Trả về downloadUrl để app mở / tải về.
 */
exports.exportAttendeesExcel = onCall(
  { region: REGION },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Bạn phải đăng nhập để export attendees.",
      );
    }

    const eventId = request.data && request.data.eventId;
    if (!eventId) {
      throw new HttpsError("invalid-argument", "Missing eventId");
    }

    // 🔹 ĐỔI: lấy từ collection "orders" giống màn OrganizerAttendeesActivity
    const ordersSnap = await db.collection("orders")
      .where("eventId", "==", eventId)
      .get();

    if (ordersSnap.empty) {
      return {
        ok: true,
        downloadUrl: null,
        message: "Không có đơn đặt vé nào cho sự kiện này.",
      };
    }

    const rows = [];
    // header
    rows.push("orderId,userId,totalTickets,totalAmount,createdAt");

    ordersSnap.forEach((doc) => {
      const o = doc.data() || {};
      const orderId = doc.id.replace(/,/g, " ");
      const userId = (o.userId || "").toString().replace(/,/g, " ");
      const totalTickets = o.totalTickets || 0;
      const totalAmount = o.totalAmount || o.originalAmount || 0;
      const createdAt = o.createdAt && o.createdAt.toDate
        ? o.createdAt.toDate().toISOString()
        : "";

      rows.push(
        `${orderId},${userId},${totalTickets},${totalAmount},${createdAt}`,
      );
    });

    const csvContent = rows.join("\n");

    const bucket = admin.storage().bucket();
    const filePath = `exports/${eventId}/orders-${Date.now()}.csv`;
    const file = bucket.file(filePath);

    await file.save(csvContent, {
      contentType: "text/csv",
    });

    const [url] = await file.getSignedUrl({
      action: "read",
      expires: "2100-01-01",
    });

    return {
      ok: true,
      downloadUrl: url,
      path: filePath,
    };
  },
);


/**
 * Stub export PDF – tạm thời chưa implement, nhưng giữ để app gọi không bị missing function.
 * data: { eventId }
 */
exports.exportAttendeesPdf = onCall(
  {region: REGION},
  async (request) => {
    if (!request.auth) {
      throw new HttpsError(
        "unauthenticated",
        "Bạn phải đăng nhập để export PDF.",
      );
    }

    const eventId = request.data && request.data.eventId;
    if (!eventId) {
      throw new HttpsError("invalid-argument", "Missing eventId");
    }

    // TODO: nếu sau này cần PDF thật, ta add thư viện (vd: pdfkit) và generate file.
    return {
      ok: false,
      message: "PDF export chưa được triển khai.",
    };
  },
);
