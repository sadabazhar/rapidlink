package com.rapidlink.services;

public interface QrCodeService {

    byte[] getQrCode(String shortCode, int size);
}