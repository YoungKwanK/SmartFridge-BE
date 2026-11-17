package com.be16_2nd.SmartFridge.food.dto;

import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 여러 식품을 한 번에 처리하는 요청. 예: { "foodIds": [3, 7, 12], "result": "폐기" }
 * 하나라도 처리할 수 없으면 전부 처리하지 않는다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoodBulkFinishDto {
    @NotEmpty(message = "처리할 식품을 선택해 주세요.")
    private List<Long> foodIds;

    @NotNull(message = "처리 결과(소비 또는 폐기)를 선택해 주세요.")
    private FoodResult result;
}
