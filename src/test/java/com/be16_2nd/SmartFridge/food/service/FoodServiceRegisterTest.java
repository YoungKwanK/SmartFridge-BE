package com.be16_2nd.SmartFridge.food.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import com.be16_2nd.SmartFridge.food.dto.FoodCreateDto;
import com.be16_2nd.SmartFridge.food.dto.FoodResDto;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 식품 여러 개 등록(registerFood) 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인하므로 로컬 DB에 영향이 없다.
 */
@ExtendWith(MockitoExtension.class)
class FoodServiceRegisterTest {

    @Mock FoodRepository foodRepository;
    @Mock FridgeAccessValidator fridgeAccessValidator;

    @InjectMocks FoodService foodService;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member member = Member.builder().name("홍길동").email("user@test.com").build();

    @BeforeEach
    void setUp() {
        lenient().when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, member, Type.COMMON));
        // saveAll은 받은 목록을 그대로 돌려준다고 가정
        lenient().when(foodRepository.saveAll(anyList()))
                .thenAnswer(invocation -> new ArrayList<Food>(invocation.getArgument(0)));
    }

    @Test
    void 식품_두개를_보내면_두개_저장되고_두개_돌려준다() {
        List<FoodCreateDto> list = List.of(food("우유"), food("계란"));

        List<FoodResDto> result = foodService.registerFood(fridgeId, list);

        assertThat(result).extracting(FoodResDto::getName).containsExactly("우유", "계란");
        assertThat(result).extracting(FoodResDto::getRegisteredName).containsOnly("홍길동");
        verify(foodRepository, times(1)).saveAll(anyList());
    }

    @Test
    void 식품_하나만_보내도_등록된다() {
        List<FoodResDto> result = foodService.registerFood(fridgeId, List.of(food("우유")));

        assertThat(result).hasSize(1);
    }

    @Test
    void 빈_목록이면_오류가_나고_저장하지_않는다() {
        assertThatThrownBy(() -> foodService.registerFood(fridgeId, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("등록할 식품이 없습니다.");

        verify(foodRepository, never()).saveAll(anyList());
    }

    @Test
    void 목록이_null이면_오류가_난다() {
        assertThatThrownBy(() -> foodService.registerFood(fridgeId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 삼십개까지는_등록된다() {
        List<FoodResDto> result = foodService.registerFood(fridgeId, foods(30));

        assertThat(result).hasSize(30);
    }

    @Test
    void 삼십한개면_오류가_나고_저장하지_않는다() {
        assertThatThrownBy(() -> foodService.registerFood(fridgeId, foods(31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대 30개");

        verify(foodRepository, never()).saveAll(anyList());
    }

    @Test
    void 하나라도_유통기한이_없으면_오류가_나고_아무것도_저장하지_않는다() {
        FoodCreateDto noExpiration = food("삼겹살");
        noExpiration.setExpirationDate(null);
        List<FoodCreateDto> list = List.of(food("우유"), noExpiration);

        assertThatThrownBy(() -> foodService.registerFood(fridgeId, list))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유통기한 날짜는 필수입니다.");

        verify(foodRepository, never()).saveAll(anyList());
    }

    private FoodCreateDto food(String name) {
        return FoodCreateDto.builder()
                .name(name)
                .quantity(1)
                .category(Category.기타)
                .storageType(StorageType.냉장)
                .expirationDate(LocalDate.now().plusDays(7))
                .shareable(false)
                .build();
    }

    private List<FoodCreateDto> foods(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> food("식품" + i)).toList();
    }
}
