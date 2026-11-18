package com.be16_2nd.SmartFridge.agent.chat;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.be16_2nd.SmartFridge.agent.dto.AgentActionResDto;
import com.be16_2nd.SmartFridge.agent.service.AgentActionService;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import com.be16_2nd.SmartFridge.food.dto.FoodFilterType;
import com.be16_2nd.SmartFridge.food.dto.FoodResDto;
import com.be16_2nd.SmartFridge.food.dto.FoodSearchDto;
import com.be16_2nd.SmartFridge.food.service.FoodService;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Claude가 요청한 도구를 실제로 실행한다. 모두 기존 서비스를 그대로 불러서
 * 화면과 같은 권한 규칙(관리자는 전부, 일반 멤버는 공유 식품과 내 식품)이 적용된다.
 *
 * 결과는 Claude가 읽을 JSON 문자열이다. 날짜 계산(남은 일수)은 여기서 해서 넘긴다 (모델이 계산하다 틀리지 않게).
 * 사용자 실수나 권한 문제로 실패하면 예외 대신 오류 결과를 돌려줘서 Claude가 사용자에게 설명하게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolExecutor {

    static final int MAX_FIND_RESULTS = 50;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FoodService foodService;
    private final AgentActionService agentActionService;

    /** 도구 실행 결과. 제안 도구였다면 만들어진 제안(확인 카드)을 함께 담는다 */
    public record ToolOutcome(String content, boolean error, AgentActionResDto action) {
        static ToolOutcome ok(Object body) {
            return new ToolOutcome(toJson(body), false, null);
        }

        static ToolOutcome proposed(Object body, AgentActionResDto action) {
            return new ToolOutcome(toJson(body), false, action);
        }

        static ToolOutcome error(String message) {
            return new ToolOutcome(toJson(Map.of("error", message)), true, null);
        }
    }

    // 도구 입력 형식 (AgentTools의 스키마와 같다)
    record FindFoodsInput(String keyword, Category category, String status, String scope) {}
    record FoodIdInput(Long foodId) {}
    record ProposeFinishInput(List<Long> foodIds, FoodResult result) {}

    public ToolOutcome execute(Long fridgeId, Type role, String toolName, JsonValue input) {
        try {
            return switch (toolName) {
                case AgentTools.FIND_FOODS -> findFoods(fridgeId, role, input.convert(FindFoodsInput.class));
                case AgentTools.GET_FOOD_DETAIL -> getFoodDetail(fridgeId, input.convert(FoodIdInput.class));
                case AgentTools.PROPOSE_FINISH -> proposeFinish(fridgeId, input.convert(ProposeFinishInput.class));
                case AgentTools.PROPOSE_DELETE -> proposeDelete(fridgeId, input.convert(FoodIdInput.class));
                default -> ToolOutcome.error("알 수 없는 도구입니다: " + toolName);
            };
        } catch (EntityNotFoundException | IllegalArgumentException | AccessDeniedException e) {
            log.info("에이전트 도구 실패 tool={} reason={}", toolName, e.getMessage());
            return ToolOutcome.error(e.getMessage());
        }
    }

    private ToolOutcome findFoods(Long fridgeId, Type role, FindFoodsInput input) {
        String scope = input.scope() == null ? "VISIBLE" : input.scope();
        Boolean isShared = switch (scope) {
            case "MINE" -> false;
            case "SHARED" -> true;
            default -> null;
        };
        FoodSearchDto search = FoodSearchDto.builder()
                .foodName(input.keyword())
                .category(input.category())
                .filterType(toFilterType(input.status()))
                // 관리자는 "볼 수 있는 전부"가 냉장고 전체다 (식품 목록 화면의 전체 보기와 같다)
                .viewAll(role == Type.MANAGER && "VISIBLE".equals(scope))
                .build();

        List<FoodResDto> foods = foodService.getFoodsByRole(fridgeId, search, isShared);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", foods.size());
        body.put("foods", foods.stream().limit(MAX_FIND_RESULTS).map(food -> summary(food, false)).toList());
        if (foods.size() > MAX_FIND_RESULTS) {
            body.put("note", "결과가 많아 유통기한이 가까운 " + MAX_FIND_RESULTS + "개만 보여 줍니다. 조건을 좁혀 다시 찾을 수 있습니다.");
        }
        return ToolOutcome.ok(body);
    }

    private ToolOutcome getFoodDetail(Long fridgeId, FoodIdInput input) {
        if (input.foodId() == null) {
            return ToolOutcome.error("foodId가 필요합니다.");
        }
        FoodResDto food = foodService.getHandleableFoods(fridgeId, List.of(input.foodId())).get(0);
        return ToolOutcome.ok(summary(food, true));
    }

    private ToolOutcome proposeFinish(Long fridgeId, ProposeFinishInput input) {
        AgentActionResDto action = agentActionService.proposeFinish(fridgeId, input.foodIds(), input.result());
        return ToolOutcome.proposed(proposalBody(action), action);
    }

    private ToolOutcome proposeDelete(Long fridgeId, FoodIdInput input) {
        AgentActionResDto action = agentActionService.proposeDelete(fridgeId, input.foodId());
        return ToolOutcome.proposed(proposalBody(action), action);
    }

    private Map<String, Object> proposalBody(AgentActionResDto action) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "확인 대기");
        body.put("message", "사용자 화면에 확인 카드를 띄웠습니다. 아직 처리되지 않았고, 사용자가 버튼을 눌러야 처리됩니다.");
        body.put("foods", action.getFoods().stream().map(FoodResDto::getName).toList());
        return body;
    }

    private static FoodFilterType toFilterType(String status) {
        if (status == null) {
            return FoodFilterType.ALL;
        }
        return switch (status) {
            case "EXPIRED" -> FoodFilterType.EXPIRED;
            case "TODAY" -> FoodFilterType.EXPIRES_TODAY;
            case "SOON" -> FoodFilterType.EXPIRES_IN_THREE_DAYS;
            default -> FoodFilterType.ALL;
        };
    }

    // Claude에게 넘길 식품 정보. 메모는 자세히 볼 때만 넣는다 (사용자가 쓴 글이라 꼭 필요할 때만)
    static Map<String, Object> summary(FoodResDto food, boolean detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", food.getId());
        map.put("name", food.getName());
        map.put("quantity", food.getQuantity());
        map.put("category", food.getCategory() == null ? null : food.getCategory().name());
        map.put("storageType", food.getStorageType() == null ? null : food.getStorageType().name());
        map.put("isShared", Boolean.TRUE.equals(food.getShareable()));
        map.put("registeredBy", food.getRegisteredName());
        if (food.getExpirationDateTime() != null) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), food.getExpirationDateTime().toLocalDate());
            map.put("expiration", food.getExpirationDateTime().format(DATE_TIME));
            map.put("daysLeft", daysLeft);
            map.put("expirationStatus", daysLeft < 0 ? (-daysLeft) + "일 지남" : daysLeft == 0 ? "오늘까지" : daysLeft + "일 남음");
        }
        if (detail) {
            map.put("storageStartDate", food.getStorageStartDate() == null ? null : food.getStorageStartDate().toString());
            map.put("memo", food.getMemo());
        }
        return map;
    }

    private static String toJson(Object body) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("도구 결과를 JSON으로 바꾸지 못했습니다.", e);
        }
    }
}
