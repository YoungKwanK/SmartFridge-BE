package com.be16_2nd.SmartFridge.recipe.repository;

import com.be16_2nd.SmartFridge.recipe.domain.RecipeIngredient;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    /**
     * 가진 재료와 겹치는 레시피를 추천 순서대로 찾는다. 기본 양념은 세지 않는다.
     * 순서: 부족한 재료 수 적은 순 → 가진 재료 수 많은 순 → 유통기한 임박 재료를 많이 쓰는 순 → 레시피 번호 순
     *
     * @param ownedIds    가진 재료 id (비어 있으면 안 된다)
     * @param expiringIds 그중 유통기한이 임박한 재료 id (비어 있으면 안 된다, 없으면 존재하지 않는 id 하나를 넘긴다)
     */
    @Query("""
            select new com.be16_2nd.SmartFridge.recipe.repository.RecipeMatch(
                ri.recipe.id,
                sum(case when i.id in :ownedIds then 1 else 0 end),
                sum(case when i.id in :expiringIds then 1 else 0 end),
                count(ri))
            from RecipeIngredient ri join ri.ingredient i
            where i.pantry = false
            group by ri.recipe.id
            having sum(case when i.id in :ownedIds then 1 else 0 end) > 0
            order by count(ri) - sum(case when i.id in :ownedIds then 1 else 0 end) asc,
                     sum(case when i.id in :ownedIds then 1 else 0 end) desc,
                     sum(case when i.id in :expiringIds then 1 else 0 end) desc,
                     ri.recipe.id asc
            """)
    List<RecipeMatch> findBestMatches(@Param("ownedIds") Collection<Long> ownedIds,
                                      @Param("expiringIds") Collection<Long> expiringIds,
                                      Limit limit);

    /**
     * 고른 재료를 하나라도 쓰는 레시피를 찾는다. 기본 양념은 세지 않는다.
     * 순서: 고른 재료를 많이 쓰는 순 → 고른 재료와 냉장고 재료 말고 더 필요한 재료가 적은 순 → 레시피 번호 순
     *
     * @param selectedIds  고른 재료 id (비어 있으면 안 된다)
     * @param availableIds 고른 재료와 냉장고에 있는 재료 id
     */
    @Query("""
            select new com.be16_2nd.SmartFridge.recipe.repository.RecipePickMatch(
                ri.recipe.id,
                sum(case when i.id in :selectedIds then 1 else 0 end),
                sum(case when i.id in :availableIds then 1 else 0 end),
                count(ri))
            from RecipeIngredient ri join ri.ingredient i
            where i.pantry = false
            group by ri.recipe.id
            having sum(case when i.id in :selectedIds then 1 else 0 end) > 0
            order by sum(case when i.id in :selectedIds then 1 else 0 end) desc,
                     count(ri) - sum(case when i.id in :availableIds then 1 else 0 end) asc,
                     ri.recipe.id asc
            """)
    List<RecipePickMatch> findBySelectedIngredients(@Param("selectedIds") Collection<Long> selectedIds,
                                                    @Param("availableIds") Collection<Long> availableIds,
                                                    Limit limit);

    @Query("select ri from RecipeIngredient ri join fetch ri.ingredient where ri.recipe.id in :recipeIds order by ri.id")
    List<RecipeIngredient> findAllWithIngredientByRecipeIdIn(@Param("recipeIds") Collection<Long> recipeIds);
}
