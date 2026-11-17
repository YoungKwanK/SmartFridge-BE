package com.be16_2nd.SmartFridge.recipe.dto;

import java.util.List;

/**
 * 레시피 적재 결과
 *
 * @param totalCount         원본 전체 레시피 수
 * @param created            새로 넣은 레시피 수
 * @param updated            덮어쓴 레시피 수
 * @param failedRanges       받거나 넣지 못한 구간 (예: "201-300"). 다시 실행하면 이어서 채워진다
 * @param unknownIngredients 재료 사전에 없는 이름과 등장한 레시피 수 (많은 순, 사전 보강용)
 * @param sampleKey          시험용 키로 실행했는지 (몇 건만 적재된다)
 */
public record RecipeImportResDto(
        int totalCount,
        int created,
        int updated,
        List<String> failedRanges,
        List<UnknownIngredient> unknownIngredients,
        boolean sampleKey
) {
    public record UnknownIngredient(String name, long recipeCount) {
    }
}
