package com.example.myapplication.common.util;

import android.graphics.Bitmap;

import com.journeyapps.barcodescanner.BarcodeEncoder;

public class QrUtils {

    public static Bitmap generateQr(String content, int sizePx) {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            return encoder.encodeBitmap(content,
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    sizePx, sizePx);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
