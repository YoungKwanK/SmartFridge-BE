package com.be16_2nd.SmartFridge.recipe.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 재료 마스터. 레시피 재료와 냉장고 식품을 같은 이름으로 맞추는 기준이다.
 * 이름은 재료 사전(recipe/ingredient-dictionary.txt)의 대표 이름으로 정규화된 값이다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_ingredient_name", columnNames = "name"))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // 소금·설탕·식용유처럼 어느 집에나 있는 기본 양념 (추천할 때 보유 여부를 따지지 않는다)
    @Column(nullable = false)
    private boolean pantry;

    public void changePantry(boolean pantry) {
        this.pantry = pantry;
    }
}
