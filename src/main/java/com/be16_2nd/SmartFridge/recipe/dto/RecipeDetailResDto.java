package com.be16_2nd.SmartFridge.recipe.dto;

import java.util.List;

/**
 * 레시피 상세. 재료마다 이 냉장고에 있는지를 함께 돌려준다 (재료 수에 기본 양념은 들어가지 않는다)
 *
 * @param recipeId      레시피 id
 * @param name          레시피 이름
 * @param dishType      요리 종류 (반찬, 국&찌개 등)
 * @param cookingMethod 조리 방법 (끓이기, 볶기 등)
 * @param mainImageUrl  큰 이미지
 * @param thumbnailUrl  작은 이미지
 * @param calories      1인분 열량 (kcal)
 * @param tip           저감 조리법 팁
 * @param matchedCount  냉장고에 있는 재료 수
 * @param totalCount    레시피 재료 수
 * @param ingredients   재료 (레시피 재료 순서, 기본 양념 포함)
 * @param steps         조리 단계 (순서대로)
 */
public record RecipeDetailResDto(
        Long recipeId,
        String name,
        String dishType,
        String cookingMethod,
        String mainImageUrl,
        String thumbnailUrl,
        Integer calories,
        String tip,
        long matchedCount,
        long totalCount,
        List<IngredientItem> ingredients,
        List<Step> steps
) {
    /**
     * @param name     재료 대표 이름
     * @param amount   분량 (없으면 null)
     * @param pantry   기본 양념 여부 (있는지 따지지 않는다)
     * @param owned    냉장고에 있는지 (기본 양념은 항상 false)
     * @param expiring 냉장고에 있고 유통기한이 임박했는지
     */
    public record IngredientItem(String name, String amount, boolean pantry, boolean owned, boolean expiring) {
    }

    public record Step(int order, String description, String imageUrl) {
    }
}
