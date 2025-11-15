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
