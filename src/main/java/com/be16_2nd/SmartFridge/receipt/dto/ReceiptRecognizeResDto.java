package com.be16_2nd.SmartFridge.receipt.dto;

import java.util.List;

/**
 * 영수증·주문내역 사진 인식 결과
 *
 * @param sourceType 사진 종류
 * @param foods      식품 등록 칸에 채울 초안 (비어 있으면 인식된 식품 없음)
 * @param note       AI가 남긴 참고 문구 (예: 구매일이 보이지 않음, 영수증이 아님). 없으면 null
 */
public record ReceiptRecognizeResDto(
        ReceiptSourceType sourceType,
        List<ReceiptFoodDraftDto> foods,
        String note
) {}
