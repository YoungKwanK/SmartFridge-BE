package com.be16_2nd.SmartFridge.recipe.dto;

import java.util.List;

/**
 * 레시피 이름 검색 결과 한 페이지
 *
 * @param recipes 검색어에 가까운 순서대로 (재료마다 이 냉장고에 있는지를 붙인다)
 * @param hasNext 다음 페이지가 있으면 true
 */
public record RecipeSearchResDto(
        List<RecipeRecommendResDto> recipes,
        boolean hasNext
) {
}
