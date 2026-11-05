package com.be16_2nd.SmartFridge.food.dto;

import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.member.domain.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FoodCreateDto {
    private String name;
    private Integer quantity;
    private StorageType storageType;
    private Category category;

    private LocalDate expirationDate;
    private Integer expirationHour;
    private Integer expirationMinute;

    private String memo;
    private Boolean shareable;

    public Food toEntity(Member member, Fridge fridge) {
        if (expirationDate == null) {
            throw new IllegalArgumentException("유통기한 날짜는 필수입니다.");
        }
        int hour = (expirationHour != null) ? expirationHour : 23;
        int minute = (expirationMinute != null) ? expirationMinute : 59;
        LocalDateTime expirationDateTime = expirationDate.atTime(hour, minute);

        return Food.builder()
                .name(name)
                .quantity(quantity)
                .storageType(storageType)
                .category(category)
                .storageStartDate(LocalDate.now())
                .expirationDateTime(expirationDateTime)
                .memo(memo)
                .isShared(shareable)
                .member(member)
                .fridge(fridge)
                .build();
    }
}
