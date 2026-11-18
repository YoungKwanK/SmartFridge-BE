package com.be16_2nd.SmartFridge.receipt.service;

import com.be16_2nd.SmartFridge.common.ai.AiImage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptImageValidatorTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 0x00};
    // 아이폰 기본 사진 형식 (ftypheic)
    static final byte[] HEIC = {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};

    @Test
    void 파일_시그니처로_형식을_판별한다() {
        assertThat(validate(JPEG, "image/png").mediaType()).isEqualTo("image/jpeg");
        assertThat(validate(PNG, "image/jpeg").mediaType()).isEqualTo("image/png");
        assertThat(validate(WEBP, "image/jpeg").mediaType()).isEqualTo("image/webp");
    }

    @Test
    void 지원하지_않는_형식은_막는다() {
        assertThatThrownBy(() -> validate(HEIC, "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jpg, png, webp");
        assertThatThrownBy(() -> validate("<html>".getBytes(), "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 사진이_없거나_비어_있으면_막는다() {
        assertThatThrownBy(() -> ReceiptImageValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사진을 올려");
        assertThatThrownBy(() -> validate(new byte[0], "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 용량이_5MB를_넘으면_막는다() {
        byte[] large = new byte[(int) ReceiptImageValidator.MAX_BYTES + 1];
        System.arraycopy(JPEG, 0, large, 0, JPEG.length);

        assertThatThrownBy(() -> validate(large, "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    private static AiImage validate(byte[] content, String contentType) {
        return ReceiptImageValidator.validate(new MockMultipartFile("image", "receipt", contentType, content));
    }
}
