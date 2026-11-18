package com.jjinmak.back.boundedContext.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

// 업로드를 허용하는 이미지 형식. 파일명이나 클라이언트가 보낸 Content-Type이 아니라 파일 앞부분(매직 넘버)으로 판별한다.
@Getter
@RequiredArgsConstructor
public enum ProductImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    public static Optional<ProductImageFormat> detect(byte[] bytes) {
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        // "RIFF" + 파일 크기(4바이트) + "WEBP"
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int offset, int... signature) {
        if (bytes == null || bytes.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
