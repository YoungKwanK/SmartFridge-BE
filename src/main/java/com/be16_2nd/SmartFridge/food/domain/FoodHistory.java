package com.be16_2nd.SmartFridge.food.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 냉장고에서 나간 식품의 기록 (다 먹음 또는 버림).
 * 월간 리포트와 폐기 확률 예측에 쓰려고, 식품을 삭제하기 전에 그 당시 값을 복사해 둔다.
 *
 * 냉장고와 회원은 연관관계 대신 id 값만 저장한다.
 * 회원이 탈퇴하거나 냉장고가 삭제되어도 외래키 때문에 삭제가 막히지 않고, 기록은 그대로 남는다.
 */
@Entity
@Table(name = "food_history",
        indexes = @Index(name = "idx_food_history_fridge_ended", columnList = "fridge_id, ended_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class FoodHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fridge_id", nullable = false)
    private Long fridgeId;

    // 식품을 등록한 회원. 등록자 없음 식품이면 null
    @Column(name = "member_id")
    private UUID memberId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Enumerated(EnumType.STRING)
    private StorageType storageType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Boolean isShared;

    // 등록일
    @Column(nullable = false)
    private LocalDate storageStartDate;

    @Column(nullable = false)
    private LocalDateTime expirationDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodResult result;

    // 다 먹음 또는 버림으로 처리한 시각
    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    // 식품을 통째로 정리할 때: 남은 수량 전부를 기록한다
    public static FoodHistory of(Food food, FoodResult result, LocalDateTime endedAt) {
        return of(food, result, food.getQuantity(), endedAt);
    }

    // 수량 일부만 먹거나 버렸을 때: 줄어든 수량만 기록한다
    public static FoodHistory of(Food food, FoodResult result, int quantity, LocalDateTime endedAt) {
        return FoodHistory.builder()
                .fridgeId(food.getFridge().getId())
                .memberId(food.getMember() == null ? null : food.getMember().getId())
                .name(food.getName())
                .category(food.getCategory())
                .storageType(food.getStorageType())
                .quantity(quantity)
                .isShared(food.getIsShared())
                .storageStartDate(food.getStorageStartDate())
                .expirationDateTime(food.getExpirationDateTime())
                .result(result)
                .endedAt(endedAt)
                .build();
    }
}
