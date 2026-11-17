package com.be16_2nd.SmartFridge.recipe.domain;

import com.be16_2nd.SmartFridge.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 레시피. 식품안전나라 조리식품 레시피 DB(COOKRCP01)에서 적재한다.
 * 같은 원본(sourceId)을 다시 적재하면 새로 만들지 않고 내용을 덮어쓴다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_recipe_source_id", columnNames = "source_id"))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 원본 레시피 번호 (RCP_SEQ)
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private String name;

    // 요리 종류 (반찬, 국&찌개, 일품, 후식 등)
    @Column(length = 50)
    private String dishType;

    // 조리 방법 (찌기, 끓이기, 볶기 등)
    @Column(length = 50)
    private String cookingMethod;

    // 원본 재료 문자열. 정규화 규칙을 바꿨을 때 다시 파싱하거나 화면에 그대로 보여줄 때 쓴다
    @Column(columnDefinition = "TEXT")
    private String ingredientsText;

    @Column(length = 500)
    private String mainImageUrl;

    @Column(length = 500)
    private String thumbnailUrl;

    // 1인분 열량 (kcal)
    private Integer calories;

    @Column(columnDefinition = "TEXT")
    private String tip;

    @Builder.Default
    @BatchSize(size = 100)
    @OrderBy("stepOrder ASC")
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeStep> steps = new ArrayList<>();

    @Builder.Default
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    public void updateInfo(String name, String dishType, String cookingMethod, String ingredientsText,
                           String mainImageUrl, String thumbnailUrl, Integer calories, String tip) {
        this.name = name;
        this.dishType = dishType;
        this.cookingMethod = cookingMethod;
        this.ingredientsText = ingredientsText;
        this.mainImageUrl = mainImageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.calories = calories;
        this.tip = tip;
    }

    /**
     * 조리 단계를 주어진 목록으로 맞춘다.
     * 지우고 다시 넣으면 Hibernate 가 INSERT 를 DELETE 보다 먼저 실행해 (recipe_id, step_order) 유니크에 걸리므로
     * 같은 순서의 행은 내용만 고치고, 남는 행만 지우고, 모자란 행만 추가한다.
     */
    public void replaceSteps(List<StepContent> contents) {
        for (int i = 0; i < contents.size(); i++) {
            StepContent content = contents.get(i);
            if (i < steps.size()) {
                steps.get(i).update(content.description(), content.imageUrl());
            } else {
                steps.add(RecipeStep.builder()
                        .recipe(this)
                        .stepOrder(i + 1)
                        .description(content.description())
                        .imageUrl(content.imageUrl())
                        .build());
            }
        }
        while (steps.size() > contents.size()) {
            steps.remove(steps.size() - 1);
        }
    }

    /**
     * 재료를 주어진 목록(재료 → 분량)으로 맞춘다. 순서를 지키려면 LinkedHashMap 을 넘긴다.
     * 단계와 같은 이유로 (recipe_id, ingredient_id) 가 같은 행은 분량만 고친다.
     */
    public void replaceIngredients(LinkedHashMap<Ingredient, String> amounts) {
        Map<Long, String> amountById = new HashMap<>();
        amounts.forEach((ingredient, amount) -> amountById.put(ingredient.getId(), amount));

        ingredients.removeIf(ri -> !amountById.containsKey(ri.getIngredient().getId()));
        Map<Long, RecipeIngredient> existing = new HashMap<>();
        ingredients.forEach(ri -> existing.put(ri.getIngredient().getId(), ri));

        amounts.forEach((ingredient, amount) -> {
            RecipeIngredient current = existing.get(ingredient.getId());
            if (current != null) {
                current.changeAmount(amount);
            } else {
                ingredients.add(RecipeIngredient.builder()
                        .recipe(this)
                        .ingredient(ingredient)
                        .amount(amount)
                        .build());
            }
        });
    }

    public record StepContent(String description, String imageUrl) {
    }
}
