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

    // 시각을 비우면 그날의 끝(23:59)까지로 본다. 등록과 수정이 같은 규칙을 쓴다
    private static final int DEFAULT_EXPIRATION_HOUR = 23;
    private static final int DEFAULT_EXPIRATION_MINUTE = 59;

    /**
     * 화면에서 받은 날짜와 시각으로 유통기한을 만든다.
     * 날짜는 꼭 있어야 하고, 시각(시·분)은 비우면 23:59로 채운다. 범위를 벗어난 시각은 400으로 돌려준다.
     */
    public static LocalDateTime expirationOf(LocalDate date, Integer hour, Integer minute) {
        if (date == null) {
            throw new IllegalArgumentException("유통기한 날짜는 필수입니다.");
        }
        int h = hour != null ? hour : DEFAULT_EXPIRATION_HOUR;
        int m = minute != null ? minute : DEFAULT_EXPIRATION_MINUTE;
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            throw new IllegalArgumentException("유통기한 시각은 0시 0분부터 23시 59분 사이로 입력해 주세요.");
        }
        return date.atTime(h, m);
    }

    public Food updateFood(FoodUpdateDto foodUpdateDto) {
        LocalDateTime expirationDateTime = expirationOf(foodUpdateDto.getExpirationDate(),
                foodUpdateDto.getExpirationHour(), foodUpdateDto.getExpirationMinute());
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
