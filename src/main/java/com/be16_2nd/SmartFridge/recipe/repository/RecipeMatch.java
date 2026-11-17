package com.be16_2nd.SmartFridge.recipe.repository;

/**
 * 레시피 하나와 냉장고 재료가 겹치는 정도 (기본 양념 제외)
 *
 * @param recipeId      레시피 id
 * @param matchedCount  가진 재료 수
 * @param expiringCount 가진 재료 중 유통기한이 임박한 재료 수
 * @param totalCount    레시피의 재료 수
 */
public record RecipeMatch(Long recipeId, Long matchedCount, Long expiringCount, Long totalCount) {

    public long missingCount() {
        return totalCount - matchedCount;
    }
}
