package com.be16_2nd.SmartFridge.recipe.dto;

import java.util.List;

/**
 * 고른 재료로 찾은 레시피 하나
 *
 * @param recipe              레시피와 냉장고 기준 재료 현황 (있는 재료·더 필요한 재료·임박 재료).
 *                            고른 재료라도 냉장고에 없으면 더 필요한 재료에 들어간다
 * @param selectedIngredients 이 레시피가 쓰는 고른 재료 (레시피 재료 순서)
 */
public record RecipePickResDto(
        RecipeRecommendResDto recipe,
        List<String> selectedIngredients
) {
}
