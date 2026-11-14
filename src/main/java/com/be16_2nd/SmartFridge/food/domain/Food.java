package com.be16_2nd.SmartFridge.food.domain;

import com.be16_2nd.SmartFridge.food.dto.FoodUpdateDto;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@ToString
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private Integer quantity;

    // 두 멤버가 같은 식품을 거의 동시에 고치거나 정리하면 나중 요청을 실패시킨다 (기록이 두 번 남거나 수량이 덮어써지지 않게)
    // 이미 있는 식품도 0부터 시작하도록 DB 기본값을 둔다 (ddl-auto: update 로 컬럼이 추가될 때)
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Enumerated(EnumType.STRING)
    private Category category;
    @Enumerated(EnumType.STRING)
    private StorageType storageType;

    @Column(nullable = false)
    private LocalDate storageStartDate;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(nullable = false)
    private LocalDateTime expirationDateTime;

    private String memo;
    @Column(nullable = false)
    private Boolean isShared;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    public Food updateFood(FoodUpdateDto foodUpdateDto) {
        LocalDateTime expirationDateTime = foodUpdateDto.getExpirationDate().atTime(foodUpdateDto.getExpirationHour(), foodUpdateDto.getExpirationMinute());
        this.name = foodUpdateDto.getName();
        this.quantity = foodUpdateDto.getQuantity();
        this.storageType = foodUpdateDto.getStorageType();
        this.category =  foodUpdateDto.getCategory();
        this.expirationDateTime = expirationDateTime;
        this.memo =  foodUpdateDto.getMemo();
        this.isShared = foodUpdateDto.getShareable();
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id")
    private Fridge fridge;
}
