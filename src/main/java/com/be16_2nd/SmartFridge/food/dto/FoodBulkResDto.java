package com.be16_2nd.SmartFridge.food.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 일괄 처리 결과. 처리한 식품 수와 이름을 돌려준다 (화면 안내 문구에 사용).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoodBulkResDto {
    private int count;
    private List<String> names;
}
