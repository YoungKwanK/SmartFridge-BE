package com.be16_2nd.SmartFridge.food.service;

import com.be16_2nd.SmartFridge.common.dto.CommonErrorDto;
import com.be16_2nd.SmartFridge.common.service.CommonExceptionHandler;
import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.domain.FoodHistory;
import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import com.be16_2nd.SmartFridge.food.dto.FoodCreateDto;
import com.be16_2nd.SmartFridge.food.dto.FoodUpdateDto;
import com.be16_2nd.SmartFridge.food.repository.FoodHistoryRepository;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 수정 화면에서 수량을 줄일 때 기록을 남기는 규칙과 수량 검증 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인하므로 로컬 DB에 영향이 없다.
 */
@ExtendWith(MockitoExtension.class)
class FoodQuantityHistoryTest {

    @Mock FoodRepository foodRepository;
    @Mock FoodHistoryRepository foodHistoryRepository;
    @Mock FridgeAccessValidator fridgeAccessValidator;

    @InjectMocks FoodService foodService;

    final Long fridgeId = 1L;
    final Long foodId = 10L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member me = Member.builder().id(UUID.randomUUID()).name("홍길동").build();

    @Test
    void 수량을_줄이고_먹었어요를_고르면_줄어든_수량만큼_소비_기록이_남는다() {
        Food yogurt = givenMyFood("요구르트", 5);

        foodService.updateFood(fridgeId, foodId, updateDto("요구르트", 2, FoodResult.소비));

        FoodHistory history = savedHistory();
        assertThat(history.getResult()).isEqualTo(FoodResult.소비);
        assertThat(history.getQuantity()).isEqualTo(3);
        assertThat(history.getName()).isEqualTo("요구르트");
        assertThat(yogurt.getQuantity()).isEqualTo(2);
        verify(foodRepository, never()).delete(any());
    }

    @Test
    void 수량을_줄이고_버렸어요를_고르면_줄어든_수량만큼_폐기_기록이_남는다() {
        givenMyFood("요구르트", 5);

        foodService.updateFood(fridgeId, foodId, updateDto("요구르트", 4, FoodResult.폐기));

        FoodHistory history = savedHistory();
        assertThat(history.getResult()).isEqualTo(FoodResult.폐기);
        assertThat(history.getQuantity()).isEqualTo(1);
    }

    @Test
    void 수량만_고칠게요를_고르면_기록하지_않는다() {
        Food yogurt = givenMyFood("요구르트", 5);

        foodService.updateFood(fridgeId, foodId, updateDto("요구르트", 2, null));

        assertThat(yogurt.getQuantity()).isEqualTo(2);
        verifyNoInteractions(foodHistoryRepository);
    }

    @Test
    void 수량을_늘리면_결과를_보내도_기록하지_않는다() {
        Food yogurt = givenMyFood("요구르트", 2);

        foodService.updateFood(fridgeId, foodId, updateDto("요구르트", 5, FoodResult.소비));

        assertThat(yogurt.getQuantity()).isEqualTo(5);
        verifyNoInteractions(foodHistoryRepository);
    }

    @Test
    void 수량을_0개로_고칠_수_없다() {
        assertThatThrownBy(() -> foodService.updateFood(fridgeId, foodId, updateDto("요구르트", 0, FoodResult.소비)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1개 이상");

        verifyNoInteractions(fridgeAccessValidator, foodRepository, foodHistoryRepository);
    }

    @Test
    void 수량이_없거나_0개_이하인_식품은_등록할_수_없다() {
        List<FoodCreateDto> foods = List.of(createDto("우유", 1), createDto("계란", -3));

        assertThatThrownBy(() -> foodService.registerFood(fridgeId, foods))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1개 이상");
        assertThatThrownBy(() -> foodService.registerFood(fridgeId, List.of(createDto("두부", null))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(foodRepository, never()).saveAll(any());
    }

    @Test
    void 다른_멤버와_동시에_처리해_충돌하면_409로_안내한다() {
        ResponseEntity<?> response = new CommonExceptionHandler().optimisticLockingFailureException(
                new ObjectOptimisticLockingFailureException(Food.class, foodId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((CommonErrorDto) response.getBody()).getStatus_message()).contains("다른 멤버가 먼저");
    }

    private Food givenMyFood(String name, int quantity) {
        Food food = Food.builder()
                .id(foodId)
                .name(name)
                .quantity(quantity)
                .category(Category.기타)
                .storageType(StorageType.냉장)
                .storageStartDate(LocalDate.now())
                .expirationDateTime(LocalDateTime.now().plusDays(3))
                .isShared(false)
                .member(me)
                .fridge(fridge)
                .build();
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, Type.COMMON));
        when(foodRepository.findById(foodId)).thenReturn(Optional.of(food));
        return food;
    }

    private FoodHistory savedHistory() {
        ArgumentCaptor<FoodHistory> captor = ArgumentCaptor.forClass(FoodHistory.class);
        verify(foodHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private FoodUpdateDto updateDto(String name, int quantity, FoodResult reducedResult) {
        return FoodUpdateDto.builder()
                .name(name)
                .quantity(quantity)
                .reducedResult(reducedResult)
                .storageType(StorageType.냉장)
                .category(Category.기타)
                .expirationDate(LocalDate.now().plusDays(3))
                .expirationHour(23)
                .expirationMinute(59)
                .shareable(false)
                .build();
    }

    private FoodCreateDto createDto(String name, Integer quantity) {
        return FoodCreateDto.builder()
                .name(name)
                .quantity(quantity)
                .storageType(StorageType.냉장)
                .category(Category.기타)
                .expirationDate(LocalDate.now().plusDays(3))
                .shareable(true)
                .build();
    }
}
