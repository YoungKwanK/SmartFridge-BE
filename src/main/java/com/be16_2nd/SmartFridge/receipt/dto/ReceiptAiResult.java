package com.be16_2nd.SmartFridge.receipt.dto;

import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.StorageType;

import java.util.List;
import java.util.Optional;

/**
 * AI가 영수증·주문내역 사진을 읽고 돌려주는 원본 결과 (이 구조대로 JSON 스키마를 만들어 그 형식으로만 답하게 한다)
 * 서버가 {@link ReceiptRecognizeResDto} 로 다듬어서 내려준다.
 */
public record ReceiptAiResult(
        ReceiptSourceType sourceType,
        List<Item> items,
        Optional<String> note
) {

    /**
     * @param rawName       사진에 적힌 상품명 그대로
     * @param name          냉장고 목록에 쓸 일반 식품명
     * @param quantity      영수증 수량 칸 / 주문내역의 N개
     * @param packCount     상품명에 적힌 묶음 개수 (16개입 → 16)
     * @param amount        상품명에 적힌 용량·무게 (900g, 1L)
     * @param purchaseDate  YYYY-MM-DD, 사진에서 알 수 없으면 비어 있음
     * @param shelfLifeDays 사 온 날부터 보관 위치 기준으로 먹을 수 있는 대략적인 일수
     */
    public record Item(
            String rawName,
            String name,
            int quantity,
            Optional<Integer> packCount,
            Optional<String> amount,
            Category category,
            StorageType storage,
            Optional<String> purchaseDate,
            int shelfLifeDays
    ) {}
}
