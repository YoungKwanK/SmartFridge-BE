package com.be16_2nd.SmartFridge.inquiry.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * 문의 첨부 이미지 검증 (프론트 제한과 같은 5장, 장당 5MB)
 * - 파일 종류는 클라이언트가 보낸 확장자·Content-Type 이 아니라 실제 파일 앞부분(시그니처)으로 판별한다
 */
final class InquiryImageValidator {

    static final int MAX_IMAGES = 5;
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private InquiryImageValidator() {
    }

    // 빈 파일을 제외한 업로드 대상을 돌려준다. keptImageCount 는 수정 후에도 남는 기존 이미지 수
    static List<MultipartFile> validate(List<MultipartFile> files, int keptImageCount) {
        List<MultipartFile> images = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    images.add(file);
                }
            }
        }

        if (keptImageCount + images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("이미지는 최대 " + MAX_IMAGES + "장까지 첨부할 수 있습니다.");
        }
        for (MultipartFile image : images) {
            if (image.getSize() > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("이미지는 1장당 " + (MAX_IMAGE_BYTES / 1024 / 1024) + "MB 이하만 올릴 수 있습니다.");
            }
            validateSignature(image);
        }
        return images;
    }

    private static void validateSignature(MultipartFile image) {
        byte[] b;
        try {
            b = image.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
        }
        boolean allowed = startsWith(b, 0, 0xFF, 0xD8, 0xFF)
                || startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)
                || startsWith(b, 0, 'G', 'I', 'F', '8')
                || (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P'));
        if (!allowed) {
            throw new IllegalArgumentException("jpg, png, gif, webp 이미지만 올릴 수 있습니다.");
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
