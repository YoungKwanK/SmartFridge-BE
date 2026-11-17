package com.be16_2nd.SmartFridge.recipe.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_recipe_ingredient", columnNames = {"recipe_id", "ingredient_id"}))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    // 원본 표기 그대로의 분량 (예: "75g(3/4모)", "약간"). 없으면 null
    @Column(length = 100)
    private String amount;

    void changeAmount(String amount) {
        this.amount = amount;
    }
}
