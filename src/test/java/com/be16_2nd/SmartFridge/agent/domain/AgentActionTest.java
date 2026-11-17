package com.be16_2nd.SmartFridge.agent.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 제안 엔티티의 규칙: 상태는 처리 전(PENDING)에서 한 번만 바뀌고, 10분이 지나면 만료다.
 */
class AgentActionTest {

    final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 12, 0);
    final UUID memberId = UUID.randomUUID();

    @Test
    void 한_번_처리한_제안은_다른_상태로_바꿀_수_없다() {
        AgentAction action = AgentAction.propose(1L, memberId, AgentActionType.FINISH_CONSUMED, List.of(1L), now);
        action.cancel(now);

        assertThatThrownBy(() -> action.confirm(now)).isInstanceOf(IllegalStateException.class);
        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.CANCELLED);
    }

    @Test
    void 만든_지_10분이_되면_만료다() {
        AgentAction action = AgentAction.propose(1L, memberId, AgentActionType.FINISH_CONSUMED, List.of(1L), now);

        assertThat(action.isExpired(now.plusMinutes(9).plusSeconds(59))).isFalse();
        assertThat(action.isExpired(now.plusMinutes(10))).isTrue();
    }

    @Test
    void 삭제는_식품_1개만_제안할_수_있다() {
        assertThatThrownBy(() -> AgentAction.propose(1L, memberId, AgentActionType.DELETE, List.of(1L, 2L), now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 실패_이유는_컬럼_길이에_맞게_자른다() {
        AgentAction action = AgentAction.propose(1L, memberId, AgentActionType.FINISH_CONSUMED, List.of(1L), now);
        action.fail("가".repeat(300), now);

        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.FAILED);
        assertThat(action.getFailureReason()).hasSize(255);
    }

    @Test
    void 식품_id_목록은_쉼표로_저장했다가_그대로_읽는다() {
        FoodIdListConverter converter = new FoodIdListConverter();

        String column = converter.convertToDatabaseColumn(List.of(12L, 15L, 18L));

        assertThat(column).isEqualTo("12,15,18");
        assertThat(converter.convertToEntityAttribute(column)).containsExactly(12L, 15L, 18L);
    }
}
