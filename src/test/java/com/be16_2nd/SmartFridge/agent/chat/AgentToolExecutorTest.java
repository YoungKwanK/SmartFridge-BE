package com.be16_2nd.SmartFridge.agent.chat;

import com.anthropic.core.JsonValue;
import com.be16_2nd.SmartFridge.agent.chat.AgentToolExecutor.ToolOutcome;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionStatus;
import com.be16_2nd.SmartFridge.agent.domain.AgentActionType;
import com.be16_2nd.SmartFridge.agent.dto.AgentActionResDto;
import com.be16_2nd.SmartFridge.agent.service.AgentActionService;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import com.be16_2nd.SmartFridge.food.dto.FoodFilterType;
import com.be16_2nd.SmartFridge.food.dto.FoodResDto;
import com.be16_2nd.SmartFridge.food.dto.FoodSearchDto;
import com.be16_2nd.SmartFridge.food.service.FoodService;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 에이전트 도구 실행 단위 테스트. 기존 서비스를 화면과 같은 조건으로 부르는지, 결과를 Claude가 읽기 좋게 만드는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class AgentToolExecutorTest {

    @Mock FoodService foodService;
    @Mock AgentActionService agentActionService;

    @InjectMocks AgentToolExecutor toolExecutor;

    final Long fridgeId = 1L;

    @Test
    void 식품_찾기는_조건을_식품_목록_검색으로_바꾸고_남은_일수를_계산해_준다() {
        when(foodService.getFoodsByRole(eq(fridgeId), any(), isNull()))
                .thenReturn(List.of(food(12L, "우유", LocalDateTime.now().plusDays(1))));

        ToolOutcome outcome = toolExecutor.execute(fridgeId, Type.COMMON, AgentTools.FIND_FOODS,
                JsonValue.from(Map.of("keyword", "우유", "category", "유제품", "status", "SOON")));

        FoodSearchDto search = searched(null);
        assertThat(search.getFoodName()).isEqualTo("우유");
        assertThat(search.getCategory()).isEqualTo(Category.유제품);
        assertThat(search.getFilterType()).isEqualTo(FoodFilterType.EXPIRES_IN_THREE_DAYS);
        assertThat(search.getViewAll()).isFalse();
        assertThat(outcome.error()).isFalse();
        assertThat(outcome.content()).contains("\"id\":12").contains("\"daysLeft\":1").contains("1일 남음");
        // 목록에서는 메모를 넘기지 않는다
        assertThat(outcome.content()).doesNotContain("memo");
    }

    @Test
    void 관리자가_볼_수_있는_전부를_찾으면_냉장고_전체를_본다() {
        when(foodService.getFoodsByRole(eq(fridgeId), any(), isNull())).thenReturn(List.of());

        toolExecutor.execute(fridgeId, Type.MANAGER, AgentTools.FIND_FOODS, JsonValue.from(Map.of()));

        assertThat(searched(null).getViewAll()).isTrue();
    }

    @Test
    void 내_식품만_찾으면_공유하지_않은_식품으로_거른다() {
        when(foodService.getFoodsByRole(eq(fridgeId), any(), eq(false))).thenReturn(List.of());

        toolExecutor.execute(fridgeId, Type.MANAGER, AgentTools.FIND_FOODS, JsonValue.from(Map.of("scope", "MINE")));

        assertThat(searched(false).getViewAll()).isFalse();
    }

    @Test
    void 결과가_50개를_넘으면_50개만_넘기고_알려준다() {
        List<FoodResDto> many = LongStream.rangeClosed(1, 60)
                .mapToObj(id -> food(id, "식품" + id, LocalDateTime.now().plusDays(5)))
                .toList();
        when(foodService.getFoodsByRole(eq(fridgeId), any(), isNull())).thenReturn(many);

        ToolOutcome outcome = toolExecutor.execute(fridgeId, Type.COMMON, AgentTools.FIND_FOODS, JsonValue.from(Map.of()));

        assertThat(outcome.content()).contains("\"total\":60").contains("\"name\":\"식품50\"")
                .doesNotContain("\"name\":\"식품51\"").contains("50개만");
    }

    @Test
    void 자세히_보기는_메모까지_넘긴다() {
        FoodResDto tofu = food(12L, "두부", LocalDateTime.now().minusDays(2));
        tofu.setMemo("찌개용");
        when(foodService.getHandleableFoods(fridgeId, List.of(12L))).thenReturn(List.of(tofu));

        ToolOutcome outcome = toolExecutor.execute(fridgeId, Type.COMMON, AgentTools.GET_FOOD_DETAIL,
                JsonValue.from(Map.of("foodId", 12)));

        assertThat(outcome.content()).contains("\"memo\":\"찌개용\"").contains("2일 지남");
    }

    @Test
    void 처리_제안은_제안_서비스를_부르고_확인_카드를_함께_돌려준다() {
        AgentActionResDto action = AgentActionResDto.builder()
                .id(UUID.randomUUID()).type(AgentActionType.FINISH_CONSUMED).status(AgentActionStatus.PENDING)
                .foods(List.of(food(12L, "우유", LocalDateTime.now()))).build();
        when(agentActionService.proposeFinish(fridgeId, List.of(12L, 15L), FoodResult.소비)).thenReturn(action);

        ToolOutcome outcome = toolExecutor.execute(fridgeId, Type.COMMON, AgentTools.PROPOSE_FINISH,
                JsonValue.from(Map.of("foodIds", List.of(12, 15), "result", "소비")));

        assertThat(outcome.action()).isEqualTo(action);
        assertThat(outcome.content()).contains("확인 대기").contains("아직 처리되지 않았고");
    }

    @Test
    void 권한이_없거나_없는_식품이면_예외_대신_오류_결과를_돌려준다() {
        when(agentActionService.proposeDelete(fridgeId, 12L))
                .thenThrow(new AccessDeniedException("다른 멤버의 비공유 식품은 수정하거나 삭제할 수 없습니다: 케이크"));

        ToolOutcome outcome = toolExecutor.execute(fridgeId, Type.COMMON, AgentTools.PROPOSE_DELETE,
                JsonValue.from(Map.of("foodId", 12)));

        assertThat(outcome.error()).isTrue();
        assertThat(outcome.action()).isNull();
        assertThat(outcome.content()).contains("케이크");
    }

    @Test
    void 모르는_도구는_오류_결과로_돌려준다() {
        ToolOutcome outcome = toolExecutor.execute(fridgeId, Type.COMMON, "confirm_action", JsonValue.from(Map.of()));

        assertThat(outcome.error()).isTrue();
    }

    private FoodSearchDto searched(Boolean isShared) {
        ArgumentCaptor<FoodSearchDto> captor = ArgumentCaptor.forClass(FoodSearchDto.class);
        if (isShared == null) {
            verify(foodService).getFoodsByRole(eq(fridgeId), captor.capture(), isNull());
        } else {
            verify(foodService).getFoodsByRole(eq(fridgeId), captor.capture(), eq(isShared));
        }
        return captor.getValue();
    }

    private FoodResDto food(Long id, String name, LocalDateTime expiration) {
        return FoodResDto.builder()
                .id(id).name(name).quantity(1)
                .category(Category.유제품)
                .expirationDateTime(expiration)
                .shareable(false)
                .registeredName("홍길동")
                .build();
    }
}
