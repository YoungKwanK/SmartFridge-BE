package com.be16_2nd.SmartFridge.food.dto;

import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FoodUpdateDto {
    private String name;
    @NotNull(message = "수량을 입력해 주세요.")
    @Min(value = 1, message = "수량은 1개 이상이어야 해요. 다 먹었거나 버렸다면 정리하기를 이용해 주세요.")
    private Integer quantity;
    // 수량을 줄였을 때 줄어든 만큼을 어떻게 했는지 (소비 또는 폐기). 비우면 수량만 고친다.
    private FoodResult reducedResult;
    private StorageType storageType;
    private Category category;

    private LocalDate expirationDate;
    private Integer expirationHour;
    private Integer expirationMinute;

    private String memo;
    private Boolean shareable;
}
