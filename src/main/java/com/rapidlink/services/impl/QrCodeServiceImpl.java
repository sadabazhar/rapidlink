package com.rapidlink.services.impl;

import com.rapidlink.services.QrCodeService;
import org.springframework.stereotype.Service;

@Service
public class QrCodeServiceImpl implements QrCodeService {

    @Override
    public byte[] generateQrCode(String content, int size) {
        return new byte[0];
    }
}
