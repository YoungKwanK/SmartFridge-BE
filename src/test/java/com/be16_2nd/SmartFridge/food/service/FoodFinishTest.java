package com.be16_2nd.SmartFridge.food.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.domain.FoodHistory;
import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import com.be16_2nd.SmartFridge.food.dto.FoodFinishDto;
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
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 식품 처리(다 먹었어요 / 버렸어요 / 잘못 등록)와 수정·삭제 권한 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인하므로 로컬 DB에 영향이 없다.
 */
@ExtendWith(MockitoExtension.class)
class FoodFinishTest {

    @Mock FoodRepository foodRepository;
    @Mock FoodHistoryRepository foodHistoryRepository;
    @Mock FridgeAccessValidator fridgeAccessValidator;

    @InjectMocks FoodService foodService;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member me = Member.builder().id(UUID.randomUUID()).name("홍길동").build();
    final Member other = Member.builder().id(UUID.randomUUID()).name("김철수").build();

    // ===== 다 먹었어요 / 버렸어요 =====

    @Test
    void 내_식품을_소비로_처리하면_등록_당시_값으로_기록하고_식품을_삭제한다() {
        Food food = food(10L, me, false, fridge);
        loginAs(Type.COMMON, food);

        foodService.finishFood(fridgeId, 10L, finish(FoodResult.소비));

        FoodHistory history = savedHistory();
        assertThat(history.getResult()).isEqualTo(FoodResult.소비);
        assertThat(history.getFridgeId()).isEqualTo(fridgeId);
        assertThat(history.getMemberId()).isEqualTo(me.getId());
        assertThat(history.getName()).isEqualTo("우유");
        assertThat(history.getCategory()).isEqualTo(Category.유제품);
        assertThat(history.getStorageType()).isEqualTo(StorageType.냉장);
        assertThat(history.getQuantity()).isEqualTo(2);
        assertThat(history.getIsShared()).isFalse();
        assertThat(history.getStorageStartDate()).isEqualTo(food.getStorageStartDate());
        assertThat(history.getExpirationDateTime()).isEqualTo(food.getExpirationDateTime());
        assertThat(history.getEndedAt()).isNotNull();
        verify(foodRepository).delete(food);
    }

    @Test
    void 다른_멤버의_공유식품은_일반_멤버도_폐기로_처리할_수_있고_등록자로_기록된다() {
        Food food = food(10L, other, true, fridge);
        loginAs(Type.COMMON, food);

        foodService.finishFood(fridgeId, 10L, finish(FoodResult.폐기));

        FoodHistory history = savedHistory();
        assertThat(history.getResult()).isEqualTo(FoodResult.폐기);
        assertThat(history.getMemberId()).isEqualTo(other.getId());
        verify(foodRepository).delete(food);
    }

    @Test
    void 등록자_없음_공유식품은_회원_id_없이_기록된다() {
        Food food = food(10L, null, true, fridge);
        loginAs(Type.COMMON, food);

        foodService.finishFood(fridgeId, 10L, finish(FoodResult.소비));

        assertThat(savedHistory().getMemberId()).isNull();
        verify(foodRepository).delete(food);
    }

    @Test
    void 관리자는_다른_멤버의_비공유식품도_처리할_수_있다() {
        Food food = food(10L, other, false, fridge);
        loginAs(Type.MANAGER, food);

        foodService.finishFood(fridgeId, 10L, finish(FoodResult.폐기));

        verify(foodHistoryRepository).save(any(FoodHistory.class));
        verify(foodRepository).delete(food);
    }

    @Test
    void 일반_멤버가_다른_멤버의_비공유식품을_처리하면_거부하고_기록도_삭제도_하지_않는다() {
        Food food = food(10L, other, false, fridge);
        loginAs(Type.COMMON, food);

        assertThatThrownBy(() -> foodService.finishFood(fridgeId, 10L, finish(FoodResult.소비)))
                .isInstanceOf(AccessDeniedException.class);
        verify(foodHistoryRepository, never()).save(any());
        verify(foodRepository, never()).delete(any());
    }

    @Test
    void 처리_결과가_없으면_거부한다() {
        assertThatThrownBy(() -> foodService.finishFood(fridgeId, 10L, new FoodFinishDto()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(foodHistoryRepository, never()).save(any());
        verify(foodRepository, never()).delete(any());
    }

    @Test
    void 다른_냉장고의_식품은_처리하지_않는다() {
        Fridge otherFridge = Fridge.builder().id(2L).fridgeName("다른 냉장고").build();
        Food food = food(10L, me, false, otherFridge);
        loginAs(Type.MANAGER, food);

        assertThatThrownBy(() -> foodService.finishFood(fridgeId, 10L, finish(FoodResult.소비)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(foodHistoryRepository, never()).save(any());
        verify(foodRepository, never()).delete(any());
    }

    // ===== 잘못 등록했어요 (기존 삭제) =====

    @Test
    void 삭제는_기록을_남기지_않고_식품만_지운다() {
        Food food = food(10L, me, false, fridge);
        loginAs(Type.COMMON, food);

        foodService.deleteFood(fridgeId, 10L);

        verify(foodHistoryRepository, never()).save(any());
        verify(foodRepository).delete(food);
    }

    @Test
    void 일반_멤버는_다른_멤버의_비공유식품을_삭제할_수_없다() {
        Food food = food(10L, other, false, fridge);
        loginAs(Type.COMMON, food);

        assertThatThrownBy(() -> foodService.deleteFood(fridgeId, 10L))
                .isInstanceOf(AccessDeniedException.class);
        verify(foodRepository, never()).delete(any());
    }

    // ===== 수정 =====

    @Test
    void 일반_멤버는_다른_멤버의_비공유식품을_수정할_수_없다() {
        Food food = food(10L, other, false, fridge);
        loginAs(Type.COMMON, food);

        assertThatThrownBy(() -> foodService.updateFood(fridgeId, 10L, new FoodUpdateDto()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(food.getName()).isEqualTo("우유");
    }

    private void loginAs(Type type, Food food) {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, type));
        when(foodRepository.findById(food.getId())).thenReturn(Optional.of(food));
    }

    private FoodHistory savedHistory() {
        ArgumentCaptor<FoodHistory> captor = ArgumentCaptor.forClass(FoodHistory.class);
        verify(foodHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private FoodFinishDto finish(FoodResult result) {
        return FoodFinishDto.builder().result(result).build();
    }

    private Food food(Long id, Member owner, boolean shared, Fridge inFridge) {
        return Food.builder()
                .id(id)
                .name("우유")
                .quantity(2)
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
