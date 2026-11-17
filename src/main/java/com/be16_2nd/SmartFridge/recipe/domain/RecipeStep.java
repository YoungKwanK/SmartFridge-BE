package com.be16_2nd.SmartFridge.recipe.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_recipe_step_order", columnNames = {"recipe_id", "step_order"}))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    // 1부터 시작하는 순서
    @Column(nullable = false)
    private int stepOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String imageUrl;

    void update(String description, String imageUrl) {
        this.description = description;
        this.imageUrl = imageUrl;
    }
}
