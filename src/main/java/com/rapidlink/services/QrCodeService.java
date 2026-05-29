package com.rapidlink.services;

public interface QrCodeService {

    byte[] generateQrCode(String content, int size);
}
