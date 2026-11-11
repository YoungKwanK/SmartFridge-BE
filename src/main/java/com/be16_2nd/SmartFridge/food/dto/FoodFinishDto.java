package com.be16_2nd.SmartFridge.food.dto;

import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 식품 처리 요청. 예: { "result": "소비" } 또는 { "result": "폐기" }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoodFinishDto {
    @NotNull(message = "처리 결과(소비 또는 폐기)를 선택해 주세요.")
    private FoodResult result;
}
