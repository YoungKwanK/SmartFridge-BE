package com.be16_2nd.SmartFridge.recipe.dto;

import java.util.List;

/**
 * AI가 다시 고른 레시피 추천 결과
 *
 * @param aiRanked AI가 순서를 정했으면 true. AI 호출이 실패해 재료 겹침 순서를 그대로 쓴 경우 false (이때 reason 은 모두 null)
 * @param recipes  추천 순서대로 정렬된 레시피
 */
public record RecipeAiRecommendResDto(
        boolean aiRanked,
        List<Item> recipes
) {
    /**
     * @param recipe 재료 겹침 추천 결과 (가진 재료·부족한 재료·임박 재료)
     * @param reason AI가 쓴 추천 이유 한 줄. AI가 고르지 않고 채워 넣은 레시피는 null
     */
    public record Item(RecipeRecommendResDto recipe, String reason) {
    }
}
