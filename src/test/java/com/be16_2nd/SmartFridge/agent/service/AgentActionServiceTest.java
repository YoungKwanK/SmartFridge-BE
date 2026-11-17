package com.be16_2nd.SmartFridge.agent.service;

import com.be16_2nd.SmartFridge.agent.domain.AgentAction;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionStatus;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionType;
import com.be16_2nd.SmartFridge.agent.dto.AgentActionConfirmResDto;
import com.be16_2nd.SmartFridge.agent.dto.AgentActionResDto;
import com.be16_2nd.SmartFridge.agent.exception.AgentActionException;
import com.be16_2nd.SmartFridge.agent.repository.AgentActionRepository;
import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import com.be16_2nd.SmartFridge.food.dto.FoodBulkFinishDto;
import com.be16_2nd.SmartFridge.food.dto.FoodBulkResDto;
import com.be16_2nd.SmartFridge.food.dto.FoodResDto;
import com.be16_2nd.SmartFridge.food.service.FoodService;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.member.domain.Member;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AI 에이전트 제안 → 확인 → 다시 검사 → 실행 단위 테스트. LLM 없이 서비스만 확인한다.
 * 핵심: 제안은 식품을 바꾸지 않고, 확인은 본인·처리 전·유효 시간 안일 때만 한 번 실행된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentActionServiceTest {

    @Mock AgentActionRepository agentActionRepository;
    @Mock AgentActionStatusRecorder statusRecorder;
    @Mock FoodService foodService;
    @Mock FridgeAccessValidator fridgeAccessValidator;

    @InjectMocks AgentActionService agentActionService;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member me = Member.builder().id(UUID.randomUUID()).name("홍길동").build();
    final Member other = Member.builder().id(UUID.randomUUID()).name("김철수").build();

    @BeforeEach
    void login() {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, Type.COMMON));
    }

    // ===== 제안 =====

    @Test
    void 처리_제안은_식품을_바꾸지_않고_확인_대기로_10분간_저장한다() {
        when(foodService.getHandleableFoods(fridgeId, List.of(12L, 15L, 12L)))
                .thenReturn(List.of(food(12L, "두부"), food(15L, "상추")));

        AgentActionResDto res = agentActionService.proposeFinish(fridgeId, List.of(12L, 15L, 12L), FoodResult.폐기);

        AgentAction saved = savedAction();
        assertThat(saved.getType()).isEqualTo(AgentActionType.FINISH_DISCARDED);
        assertThat(saved.getStatus()).isEqualTo(AgentActionStatus.PENDING);
        assertThat(saved.getMemberId()).isEqualTo(me.getId());
        assertThat(saved.getFridgeId()).isEqualTo(fridgeId);
        // 검사를 통과한 식품 id만 (중복 없이) 저장한다
        assertThat(saved.getFoodIds()).containsExactly(12L, 15L);
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plusMinutes(10));

        assertThat(res.getId()).isEqualTo(saved.getId());
        assertThat(res.getFoods()).extracting(FoodResDto::getName).containsExactly("두부", "상추");
        verify(foodService, never()).finishFoods(anyLong(), any());
        verify(foodService, never()).deleteFood(anyLong(), anyLong());
    }

    @Test
    void 다룰_수_없는_식품이_섞이면_제안을_만들지_않는다() {
        when(foodService.getHandleableFoods(fridgeId, List.of(12L, 99L)))
                .thenThrow(new AccessDeniedException("다른 멤버의 비공유 식품은 수정하거나 삭제할 수 없습니다: 케이크"));

        assertThatThrownBy(() -> agentActionService.proposeFinish(fridgeId, List.of(12L, 99L), FoodResult.소비))
                .isInstanceOf(AccessDeniedException.class);
        verify(agentActionRepository, never()).save(any());
    }

    @Test
    void 삭제_제안은_식품_1개로_만든다() {
        when(foodService.getHandleableFoods(fridgeId, List.of(12L))).thenReturn(List.of(food(12L, "두부")));

        agentActionService.proposeDelete(fridgeId, 12L);

        AgentAction saved = savedAction();
        assertThat(saved.getType()).isEqualTo(AgentActionType.DELETE);
        assertThat(saved.getFoodIds()).containsExactly(12L);
    }

    // ===== 확인 =====

    @Test
    void 확인하면_식품_서비스로_일괄_처리하고_완료로_바꾼다() {
        AgentAction action = pendingAction(me, AgentActionType.FINISH_DISCARDED, List.of(12L, 15L), LocalDateTime.now());
        when(foodService.finishFoods(eq(fridgeId), any()))
                .thenReturn(FoodBulkResDto.builder().count(2).names(List.of("두부", "상추")).build());

        AgentActionConfirmResDto res = agentActionService.confirm(fridgeId, action.getId());

        ArgumentCaptor<FoodBulkFinishDto> captor = ArgumentCaptor.forClass(FoodBulkFinishDto.class);
        verify(foodService).finishFoods(eq(fridgeId), captor.capture());
        assertThat(captor.getValue().getFoodIds()).containsExactly(12L, 15L);
        assertThat(captor.getValue().getResult()).isEqualTo(FoodResult.폐기);

        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.CONFIRMED);
        assertThat(action.getProcessedAt()).isNotNull();
        assertThat(res.getStatus()).isEqualTo(AgentActionStatus.CONFIRMED);
        assertThat(res.getResult().getNames()).containsExactly("두부", "상추");
    }

    @Test
    void 삭제_제안을_확인하면_기록_없이_식품_1개를_지운다() {
        AgentAction action = pendingAction(me, AgentActionType.DELETE, List.of(12L), LocalDateTime.now());
        when(foodService.getHandleableFoods(fridgeId, List.of(12L))).thenReturn(List.of(food(12L, "두부")));

        AgentActionConfirmResDto res = agentActionService.confirm(fridgeId, action.getId());

        verify(foodService).deleteFood(fridgeId, 12L);
        verify(foodService, never()).finishFoods(anyLong(), any());
        assertThat(res.getResult().getNames()).containsExactly("두부");
        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.CONFIRMED);
    }

    @Test
    void 다른_멤버가_받은_제안은_확인할_수_없다() {
        AgentAction action = pendingAction(other, AgentActionType.FINISH_CONSUMED, List.of(12L), LocalDateTime.now());

        assertThatThrownBy(() -> agentActionService.confirm(fridgeId, action.getId()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(foodService);
        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.PENDING);
    }

    @Test
    void 다른_냉장고의_제안은_없는_것처럼_다룬다() {
        AgentAction action = AgentAction.propose(2L, me.getId(), AgentActionType.FINISH_CONSUMED, List.of(12L), LocalDateTime.now());
        when(agentActionRepository.findById(action.getId())).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> agentActionService.confirm(fridgeId, action.getId()))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(foodService);
    }

    @Test
    void 이미_처리한_제안은_다시_실행하지_않는다() {
        AgentAction action = pendingAction(me, AgentActionType.FINISH_CONSUMED, List.of(12L), LocalDateTime.now());
        action.confirm(LocalDateTime.now());

        assertThatThrownBy(() -> agentActionService.confirm(fridgeId, action.getId()))
                .isInstanceOf(AgentActionException.class)
                .satisfies(e -> assertThat(((AgentActionException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(foodService);
    }

    @Test
    void 유효_시간이_지나면_실행하지_않고_만료로_남긴다() {
        AgentAction action = pendingAction(me, AgentActionType.FINISH_DISCARDED, List.of(12L),
                LocalDateTime.now().minusMinutes(11));

        assertThatThrownBy(() -> agentActionService.confirm(fridgeId, action.getId()))
                .isInstanceOf(AgentActionException.class)
                .satisfies(e -> assertThat(((AgentActionException) e).getStatus()).isEqualTo(HttpStatus.GONE));
        verify(statusRecorder).markExpired(action.getId());
        verifyNoInteractions(foodService);
    }

    @Test
    void 확인할_때_다시_검사해서_실패하면_실패로_남기고_오류를_그대로_알린다() {
        AgentAction action = pendingAction(me, AgentActionType.FINISH_DISCARDED, List.of(12L, 15L), LocalDateTime.now());
        // 제안 뒤에 다른 멤버가 상추를 먼저 정리한 경우
        when(foodService.finishFoods(eq(fridgeId), any()))
                .thenThrow(new EntityNotFoundException("식품을 찾을 수 없습니다. ID: [15]"));

        assertThatThrownBy(() -> agentActionService.confirm(fridgeId, action.getId()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("15");
        verify(statusRecorder).markFailed(action.getId(), "식품을 찾을 수 없습니다. ID: [15]");
        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.PENDING);
    }

    // ===== 취소 =====

    @Test
    void 취소하면_식품은_그대로_두고_취소로_바꾼다() {
        AgentAction action = pendingAction(me, AgentActionType.FINISH_CONSUMED, List.of(12L), LocalDateTime.now());

        agentActionService.cancel(fridgeId, action.getId());

        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.CANCELLED);
        verifyNoInteractions(foodService);
    }

    @Test
    void 다른_멤버가_받은_제안은_취소할_수_없다() {
        AgentAction action = pendingAction(other, AgentActionType.FINISH_CONSUMED, List.of(12L), LocalDateTime.now());

        assertThatThrownBy(() -> agentActionService.cancel(fridgeId, action.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.PENDING);
    }

    private AgentAction pendingAction(Member owner, AgentActionType type, List<Long> foodIds, LocalDateTime createdAt) {
        AgentAction action = AgentAction.propose(fridgeId, owner.getId(), type, foodIds, createdAt);
        when(agentActionRepository.findById(action.getId())).thenReturn(Optional.of(action));
        return action;
    }

    private AgentAction savedAction() {
        ArgumentCaptor<AgentAction> captor = ArgumentCaptor.forClass(AgentAction.class);
        verify(agentActionRepository).save(captor.capture());
        return captor.getValue();
    }

    private FoodResDto food(Long id, String name) {
        return FoodResDto.builder().id(id).name(name).quantity(1).build();
    }
}
