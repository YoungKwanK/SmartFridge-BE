package com.be16_2nd.SmartFridge.receipt.service;

import com.be16_2nd.SmartFridge.common.ai.AiImage;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 영수증 사진 검증. 확장자·Content-Type 은 믿지 않고 파일 시그니처로 형식을 판별한다.
 * 사진은 AI 인식에만 쓰고 어디에도 저장하지 않는다.
 */
final class ReceiptImageValidator {

    // Claude API 이미지 한 장 최대 크기. 프론트에서 긴 변 1600px JPEG 로 줄여 보내면 보통 1MB 아래다
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private ReceiptImageValidator() {
    }

    static AiImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("영수증 사진을 올려 주세요.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("사진이 너무 커요. 5MB 이하로 올려 주세요.");
        }
        byte[] bytes = readBytes(file);
        return new AiImage(bytes, detectMediaType(bytes));
    }

    static String detectMediaType(byte[] b) {
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) return "image/webp";
        throw new IllegalArgumentException("jpg, png, webp 사진만 올릴 수 있어요.");
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("사진을 읽을 수 없어요. 다시 올려 주세요.");
        }
    }

    private static boolean startsWith(byte[] b, int offset, int... signature) {
        if (b.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((b[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
