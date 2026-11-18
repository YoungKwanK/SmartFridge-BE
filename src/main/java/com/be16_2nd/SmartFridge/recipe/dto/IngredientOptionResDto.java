package com.be16_2nd.SmartFridge.recipe.dto;

/**
 * 레시피 추천에 고를 수 있는 재료 하나 (기본 양념은 고를 수 없다)
 *
 * @param ingredientId 재료 id
 * @param name         재료 이름 (재료 사전의 대표 이름)
 * @param owned        이 냉장고에 있는 재료면 true
 * @param expiring     냉장고에 있고 유통기한이 임박했으면 true
 */
public record IngredientOptionResDto(
        Long ingredientId,
        String name,
        boolean owned,
        boolean expiring
) {
}
