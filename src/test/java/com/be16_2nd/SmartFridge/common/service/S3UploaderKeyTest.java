package com.be16_2nd.SmartFridge.common.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class S3UploaderKeyTest {

    @Test
    void 같은_이름의_파일도_서로_다른_키로_저장한다() {
        assertThat(S3Uploader.createKey("photo.jpg")).isNotEqualTo(S3Uploader.createKey("photo.jpg"));
    }

    @Test
    void 확장자는_유지하고_원본_이름은_키에_쓰지_않는다() {
        String key = S3Uploader.createKey("../내 사진.png");

        assertThat(key).endsWith(".png").doesNotContain("사진").doesNotContain("/");
    }

    @Test
    void 확장자가_없거나_이상하면_확장자_없이_저장한다() {
        assertThat(S3Uploader.createKey("photo")).doesNotContain(".");
        assertThat(S3Uploader.createKey(null)).doesNotContain(".");
        assertThat(S3Uploader.createKey("a.p/ng")).doesNotContain(".");
    }
}
