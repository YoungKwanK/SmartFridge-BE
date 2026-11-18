package com.be16_2nd.SmartFridge.receipt.dto;

import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 식품 등록 칸에 채울 초안 한 개. 저장된 식품이 아니다.
 *
 * <p>앞 필드들은 FoodCreateDto 와 이름이 같아서, 사용자가 확인·수정한 뒤 그대로
 * POST /fridge/{fridgeId}/food/register 에 보내면 된다. 뒤의 참고 필드는 등록할 때 무시된다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptFoodDraftDto {

    // ===== FoodCreateDto 와 같은 필드 =====
    private String name;
    private Integer quantity;          // 묶음 상품은 주문 수량 × 묶음 개수
    private StorageType storageType;
    private Category category;
    private LocalDate expirationDate;  // 구매일(없으면 오늘) + AI가 추천한 보관 일수. 실제 표시 날짜가 아니라 추천값
    private Integer expirationHour;
    private Integer expirationMinute;
    private String memo;
    private Boolean shareable;

    // ===== 확인 화면 참고용 =====
    private String rawName;            // 사진에 적힌 상품명 그대로
    private Integer orderQuantity;     // 사진에 적힌 수량
    private Integer packCount;         // 상품명에 적힌 묶음 개수 (없으면 null)
    private String amount;             // 용량·무게 (없으면 null)
    private LocalDate purchaseDate;    // 사진에서 읽은 구매일·도착일 (없으면 null → 확인 화면에서 비어 있다고 표시)
    private Integer shelfLifeDays;     // 유통기한 추천에 쓴 보관 일수
}
