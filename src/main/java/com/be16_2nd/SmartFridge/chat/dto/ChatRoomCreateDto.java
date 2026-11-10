package com.be16_2nd.SmartFridge.chat.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ChatRoomCreateDto {
    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 20; // 프론트 CreateRoomModal 과 같은 값
    public static final String PRODUCT_URL_PATTERN = "^(https?://.*)?$";

    @NotNull(message = "냉장고를 선택해 주세요")
    private Long fridgeId;

    @NotBlank(message = "제목을 입력해 주세요")
    @Size(max = 100, message = "제목은 100자 이하로 입력해 주세요")
    private String title;

    @Size(max = 2000, message = "내용은 2000자 이하로 입력해 주세요")
    private String contents;

    // 개설자 포함 인원
    @NotNull(message = "모집 인원을 입력해 주세요")
    @Min(value = MIN_PARTICIPANTS, message = "모집 인원은 " + MIN_PARTICIPANTS + "명 이상이어야 합니다")
    @Max(value = MAX_PARTICIPANTS, message = "모집 인원은 " + MAX_PARTICIPANTS + "명 이하여야 합니다")
    private Integer maxParticipants;

    @Size(max = 255, message = "상품 링크가 너무 깁니다")
    @Pattern(regexp = PRODUCT_URL_PATTERN, message = "상품 링크는 http(s) 주소여야 합니다")
    private String productUrl;
}
