package com.be16_2nd.SmartFridge.food.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import com.be16_2nd.SmartFridge.food.dto.FoodResDto;
import com.be16_2nd.SmartFridge.food.dto.FoodSearchDto;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 식품 목록 조회(페이징 없음)와 실온 보관방식 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인하므로 로컬 DB에 영향이 없다.
 */
@ExtendWith(MockitoExtension.class)
class FoodListTest {

    @Mock FoodRepository foodRepository;
    @Mock FoodHistoryRepository foodHistoryRepository;
    @Mock FridgeAccessValidator fridgeAccessValidator;

    @InjectMocks FoodService foodService;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member me = Member.builder().id(UUID.randomUUID()).name("홍길동").build();

    @Test
    @SuppressWarnings("unchecked")
    void 페이징_없이_조건에_맞는_식품을_유통기한_가까운_순서로_전부_돌려준다() {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, Type.COMMON));
        when(foodRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(
                        food(1L, "우유", StorageType.냉장, 2),
                        food(2L, "라면", StorageType.실온, 30),
                        food(3L, "만두", StorageType.냉동, 90)));

        List<FoodResDto> result = foodService.getFoodsByRole(fridgeId, new FoodSearchDto(), true);

        assertThat(result).extracting(FoodResDto::getName).containsExactly("우유", "라면", "만두");
        assertThat(result).extracting(FoodResDto::getStorageType)
                .containsExactly(StorageType.냉장, StorageType.실온, StorageType.냉동);

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(foodRepository).findAll(any(Specification.class), sort.capture());
        Sort.Order order = sort.getValue().getOrderFor("expirationDateTime");
        assertThat(order).isNotNull();
        assertThat(order.isAscending()).isTrue();
    }

    @Test
    void 보관방식에_실온이_있다() {
        assertThat(StorageType.valueOf("실온")).isEqualTo(StorageType.실온);
        assertThat(StorageType.values()).containsExactly(StorageType.냉장, StorageType.냉동, StorageType.실온);
    }

    private Food food(Long id, String name, StorageType storageType, int daysLeft) {
        return Food.builder()
                .id(id)
                .name(name)
                .quantity(1)
                .category(Category.기타)
                .storageType(storageType)
                .storageStartDate(LocalDate.now())
                .expirationDateTime(LocalDateTime.now().plusDays(daysLeft))
                .isShared(true)
                .member(me)
                .fridge(fridge)
                .build();
    }
}
