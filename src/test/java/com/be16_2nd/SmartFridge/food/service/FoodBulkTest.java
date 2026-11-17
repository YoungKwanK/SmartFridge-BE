package com.be16_2nd.SmartFridge.food.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.domain.FoodHistory;
import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import com.be16_2nd.SmartFridge.food.dto.FoodBulkFinishDto;
import com.be16_2nd.SmartFridge.food.dto.FoodBulkResDto;
import com.be16_2nd.SmartFridge.food.repository.FoodHistoryRepository;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.member.domain.Member;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 여러 식품 한 번에 처리(소비·폐기) 단위 테스트.
 * 핵심은 "전부 되거나 전부 안 되거나": 하나라도 문제가 있으면 기록도 삭제도 하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class FoodBulkTest {

    @Mock FoodRepository foodRepository;
    @Mock FoodHistoryRepository foodHistoryRepository;
    @Mock FridgeAccessValidator fridgeAccessValidator;

    @InjectMocks FoodService foodService;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member me = Member.builder().id(UUID.randomUUID()).name("홍길동").build();
    final Member other = Member.builder().id(UUID.randomUUID()).name("김철수").build();

    // ===== 일괄 처리 (소비·폐기) =====

    @Test
    void 여러_식품을_폐기로_처리하면_식품마다_기록을_남기고_모두_삭제한다() {
        Food milk = food(10L, "우유", 2, me, false, fridge);
        Food tofu = food(11L, "두부", 1, other, true, fridge);
        loginAs(Type.COMMON, milk, tofu);

        FoodBulkResDto res = foodService.finishFoods(fridgeId, finish(FoodResult.폐기, 10L, 11L));

        List<FoodHistory> histories = savedHistories();
        assertThat(histories).hasSize(2);
        assertThat(histories).allMatch(h -> h.getResult() == FoodResult.폐기);
        assertThat(histories).extracting(FoodHistory::getName).containsExactly("우유", "두부");
        assertThat(histories).extracting(FoodHistory::getQuantity).containsExactly(2, 1);
        assertThat(histories).extracting(FoodHistory::getMemberId).containsExactly(me.getId(), other.getId());
        // 한 요청 안의 기록은 같은 처리 시각을 쓴다
        assertThat(histories.get(0).getEndedAt()).isEqualTo(histories.get(1).getEndedAt());
        verify(foodRepository).deleteAll(List.of(milk, tofu));

        assertThat(res.getCount()).isEqualTo(2);
        assertThat(res.getNames()).containsExactly("우유", "두부");
    }

    @Test
    void 같은_식품이_두_번_들어오면_한_번만_처리한다() {
        Food milk = food(10L, "우유", 2, me, false, fridge);
        loginAs(Type.COMMON, milk);

        FoodBulkResDto res = foodService.finishFoods(fridgeId, finish(FoodResult.소비, 10L, 10L));

        assertThat(savedHistories()).hasSize(1);
        assertThat(res.getCount()).isEqualTo(1);
    }

    @Test
    void 다른_멤버의_비공유식품이_하나라도_섞이면_전부_처리하지_않는다() {
        Food milk = food(10L, "우유", 2, me, false, fridge);
        Food othersCake = food(11L, "케이크", 1, other, false, fridge);
        loginAs(Type.COMMON, milk, othersCake);

        assertThatThrownBy(() -> foodService.finishFoods(fridgeId, finish(FoodResult.폐기, 10L, 11L)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("케이크");
        verifyNothingWritten();
    }

    @Test
    void 관리자는_다른_멤버의_비공유식품도_함께_처리할_수_있다() {
        Food milk = food(10L, "우유", 2, me, false, fridge);
        Food othersCake = food(11L, "케이크", 1, other, false, fridge);
        loginAs(Type.MANAGER, milk, othersCake);

        FoodBulkResDto res = foodService.finishFoods(fridgeId, finish(FoodResult.폐기, 10L, 11L));

        assertThat(res.getCount()).isEqualTo(2);
        verify(foodRepository).deleteAll(List.of(milk, othersCake));
    }

    @Test
    void 없는_식품이_하나라도_있으면_없는_ID를_알려주고_전부_처리하지_않는다() {
        Food milk = food(10L, "우유", 2, me, false, fridge);
        loginAs(Type.COMMON, milk);

        assertThatThrownBy(() -> foodService.finishFoods(fridgeId, finish(FoodResult.소비, 10L, 99L)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
        verifyNothingWritten();
    }

    @Test
    void 다른_냉장고의_식품이_섞이면_전부_처리하지_않는다() {
        Fridge otherFridge = Fridge.builder().id(2L).fridgeName("다른 냉장고").build();
        Food milk = food(10L, "우유", 2, me, false, fridge);
        Food elsewhere = food(11L, "콜라", 1, me, false, otherFridge);
        loginAs(Type.MANAGER, milk, elsewhere);

        assertThatThrownBy(() -> foodService.finishFoods(fridgeId, finish(FoodResult.소비, 10L, 11L)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNothingWritten();
    }

    @Test
    void 처리_결과가_없거나_식품을_고르지_않으면_거부한다() {
        assertThatThrownBy(() -> foodService.finishFoods(fridgeId, finish(null, 10L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> foodService.finishFoods(fridgeId, finish(FoodResult.소비)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(fridgeAccessValidator, foodRepository, foodHistoryRepository);
    }

    @Test
    void 처리는_한_번에_50개까지_가능하다() {
        Long[] fiftyOne = LongStream.rangeClosed(1, 51).boxed().toArray(Long[]::new);

        assertThatThrownBy(() -> foodService.finishFoods(fridgeId, finish(FoodResult.폐기, fiftyOne)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
        verifyNoInteractions(fridgeAccessValidator, foodRepository, foodHistoryRepository);
    }

    @Test
    void ID_목록에_빈_값이_있으면_거부한다() {
        List<Long> ids = new ArrayList<>();
        ids.add(10L);
        ids.add(null);
        FoodBulkFinishDto dto = FoodBulkFinishDto.builder().foodIds(ids).result(FoodResult.소비).build();

        assertThatThrownBy(() -> foodService.finishFoods(fridgeId, dto))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(foodRepository, foodHistoryRepository);
    }

    private void loginAs(Type type, Food... foods) {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, type));
        when(foodRepository.findAllById(anyList())).thenReturn(List.of(foods));
    }

    @SuppressWarnings("unchecked")
    private List<FoodHistory> savedHistories() {
        ArgumentCaptor<List<FoodHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodHistoryRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private void verifyNothingWritten() {
        verify(foodHistoryRepository, never()).saveAll(anyList());
        verify(foodRepository, never()).deleteAll(anyList());
    }

    private FoodBulkFinishDto finish(FoodResult result, Long... ids) {
        return FoodBulkFinishDto.builder().foodIds(List.of(ids)).result(result).build();
    }

    private Food food(Long id, String name, int quantity, Member owner, boolean shared, Fridge inFridge) {
        return Food.builder()
                .id(id)
                .name(name)
                .quantity(quantity)
                .category(Category.유제품)
                .storageType(StorageType.냉장)
                .storageStartDate(LocalDate.of(2026, 11, 1))
                .expirationDateTime(LocalDateTime.of(2026, 11, 8, 23, 59))
                .isShared(shared)
                .member(owner)
                .fridge(inFridge)
                .build();
    }
}
