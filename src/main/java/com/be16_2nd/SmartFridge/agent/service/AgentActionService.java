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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 에이전트의 제안 → 사용자 확인 → 다시 검사 → 실행.
 *
 * 제안(propose)은 에이전트 도구가 부르고, 식품을 바꾸지 않는다.
 * 확인(confirm)·취소(cancel)는 화면 버튼 전용 API로만 부를 수 있다 (에이전트 도구 목록에 없음).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AgentActionService {

    private final AgentActionRepository agentActionRepository;
    private final AgentActionStatusRecorder statusRecorder;
    private final FoodService foodService;
    private final FridgeAccessValidator fridgeAccessValidator;

    // ===== 제안 (에이전트 도구용) =====

    /** 여러 식품을 소비·폐기로 처리하자고 제안한다. 지금 다룰 수 있는 식품인지 미리 검사한다. */
    public AgentActionResDto proposeFinish(Long fridgeId, List<Long> foodIds, FoodResult result) {
        AgentActionType type = AgentActionType.finishOf(result);
        return propose(fridgeId, foodIds, type);
    }

    /** 잘못 등록한 식품 1개를 기록 없이 지우자고 제안한다. */
    public AgentActionResDto proposeDelete(Long fridgeId, Long foodId) {
        if (foodId == null) {
            throw new IllegalArgumentException("삭제할 식품을 선택해 주세요.");
        }
        return propose(fridgeId, List.of(foodId), AgentActionType.DELETE);
    }

    private AgentActionResDto propose(Long fridgeId, List<Long> foodIds, AgentActionType type) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        List<FoodResDto> foods = foodService.getHandleableFoods(fridgeId, foodIds);
        List<Long> checkedIds = foods.stream().map(FoodResDto::getId).toList();

        AgentAction action = AgentAction.propose(fridgeId, context.member().getId(), type, checkedIds, LocalDateTime.now());
        agentActionRepository.save(action);
        return AgentActionResDto.of(action, foods);
    }

    /**
     * 여러 제안의 지금 상태. 처리 전인데 10분이 지났으면 EXPIRED로 본다 (DB는 확인 버튼을 눌러야 EXPIRED로 바뀐다).
     * 기록이 없는 제안도 EXPIRED로 본다. 대화를 다시 그릴 때와 Claude에게 지난 제안의 결과를 알려 줄 때 쓴다.
     */
    @Transactional(readOnly = true)
    public Map<UUID, AgentActionStatus> currentStatuses(Collection<UUID> actionIds) {
        Map<UUID, AgentActionStatus> statuses = new HashMap<>();
        if (actionIds.isEmpty()) {
            return statuses;
        }
        LocalDateTime now = LocalDateTime.now();
        agentActionRepository.findAllById(actionIds).forEach(action -> statuses.put(action.getId(),
                action.isPending() && action.isExpired(now) ? AgentActionStatus.EXPIRED : action.getStatus()));
        actionIds.forEach(id -> statuses.putIfAbsent(id, AgentActionStatus.EXPIRED));
        return statuses;
    }

    // ===== 확인·취소 (화면 버튼 전용) =====

    /**
     * 제안을 실행한다. 본인 제안인지, 아직 처리 전인지, 유효 시간 안인지 본 뒤
     * 식품 서비스로 실행해 권한·존재·냉장고 소속을 다시 검사한다. 하나라도 실패하면 아무것도 바꾸지 않는다.
     */
    public AgentActionConfirmResDto confirm(Long fridgeId, UUID actionId) {
        AgentAction action = findOwnedPendingAction(fridgeId, actionId);

        if (action.isExpired(LocalDateTime.now())) {
            statusRecorder.markExpired(actionId);
            throw AgentActionException.expired();
        }

        FoodBulkResDto result;
        try {
            result = execute(fridgeId, action);
        } catch (EntityNotFoundException | IllegalArgumentException | AccessDeniedException e) {
            // 제안 뒤에 식품이 이미 정리됐거나 권한이 바뀐 경우. 다시 눌러도 같으니 실패로 닫는다
            statusRecorder.markFailed(actionId, e.getMessage());
            throw e;
        }

        action.confirm(LocalDateTime.now());
        return AgentActionConfirmResDto.builder()
                .id(action.getId())
                .type(action.getType())
                .status(action.getStatus())
                .result(result)
                .build();
    }

    public void cancel(Long fridgeId, UUID actionId) {
        AgentAction action = findOwnedPendingAction(fridgeId, actionId);
        action.cancel(LocalDateTime.now());
    }

    private FoodBulkResDto execute(Long fridgeId, AgentAction action) {
        if (action.getType() == AgentActionType.DELETE) {
            Long foodId = action.getFoodIds().get(0);
            List<String> names = foodService.getHandleableFoods(fridgeId, List.of(foodId)).stream()
                    .map(FoodResDto::getName)
                    .toList();
            foodService.deleteFood(fridgeId, foodId);
            return FoodBulkResDto.builder().count(1).names(names).build();
        }
        FoodBulkFinishDto dto = FoodBulkFinishDto.builder()
                .foodIds(action.getFoodIds())
                .result(action.getType().getFinishResult())
                .build();
        return foodService.finishFoods(fridgeId, dto);
    }

    /**
     * 냉장고 멤버인지 확인하고, 이 냉장고에서 내가 받은 제안 중 아직 처리 전인 것을 찾는다.
     * 다른 냉장고의 제안은 없는 것처럼 404로 응답한다 (id가 있는지조차 알려 주지 않게).
     */
    private AgentAction findOwnedPendingAction(Long fridgeId, UUID actionId) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        AgentAction action = agentActionRepository.findById(actionId)
                .filter(found -> found.getFridgeId().equals(fridgeId))
                .orElseThrow(() -> new EntityNotFoundException("제안을 찾을 수 없어요."));
        if (!action.isOwnedBy(context.member().getId())) {
            throw new AccessDeniedException("내가 받은 제안만 확인하거나 취소할 수 있어요.");
        }
        if (!action.isPending()) {
            throw AgentActionException.alreadyProcessed();
        }
        return action;
    }
}
