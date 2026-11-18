package com.be16_2nd.SmartFridge.common.ai;

/**
 * AI 요청에 같이 보내는 이미지 한 장
 *
 * @param data      이미지 원본 바이트 (base64 로 바꾸는 건 AiClient 가 한다)
 * @param mediaType image/jpeg, image/png, image/gif, image/webp 중 하나
 */
public record AiImage(byte[] data, String mediaType) {

    public AiImage {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("AI 요청 이미지가 비어 있습니다.");
        }
        if (mediaType == null || !mediaType.startsWith("image/")) {
            throw new IllegalArgumentException("AI 요청 이미지 형식이 올바르지 않습니다: " + mediaType);
        }
    }
}
