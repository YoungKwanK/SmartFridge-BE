package com.be16_2nd.SmartFridge.recipe.dto;

import java.util.List;

/**
 * 냉장고 재료로 만들 수 있는 레시피 추천 결과 하나 (재료 수에 기본 양념은 들어가지 않는다)
 *
 * @param recipeId            레시피 id
 * @param name                레시피 이름
 * @param dishType            요리 종류 (반찬, 국&찌개 등)
 * @param cookingMethod       조리 방법 (끓이기, 볶기 등)
 * @param thumbnailUrl        작은 이미지
 * @param calories            1인분 열량 (kcal)
 * @param matchedCount        냉장고에 있는 재료 수
 * @param totalCount          레시피 재료 수
 * @param matchedIngredients  냉장고에 있는 재료 (레시피 재료 순서)
 * @param missingIngredients  더 필요한 재료와 분량
 * @param expiringIngredients 냉장고에 있는 재료 중 유통기한이 임박한 재료
 */
public record RecipeRecommendResDto(
        Long recipeId,
        String name,
        String dishType,
        String cookingMethod,
        String thumbnailUrl,
        Integer calories,
        long matchedCount,
        long totalCount,
        List<String> matchedIngredients,
        List<MissingIngredient> missingIngredients,
        List<String> expiringIngredients
) {
    public record MissingIngredient(String name, String amount) {
    }
}
