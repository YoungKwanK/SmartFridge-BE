package com.be16_2nd.SmartFridge.recipe.repository;

/**
 * 고른 재료로 찾은 레시피 하나 (기본 양념 제외)
 *
 * @param recipeId       레시피 id
 * @param selectedCount  레시피 재료 중 고른 재료 수
 * @param availableCount 레시피 재료 중 고른 재료이거나 냉장고에 있는 재료 수
 * @param totalCount     레시피의 재료 수
 */
public record RecipePickMatch(Long recipeId, Long selectedCount, Long availableCount, Long totalCount) {
}
