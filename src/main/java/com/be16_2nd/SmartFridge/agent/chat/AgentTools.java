package com.be16_2nd.SmartFridge.agent.chat;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.util.List;
import java.util.Map;

/**
 * 에이전트(Claude)에게 주는 도구 4개의 정의. 실제 실행은 {@link AgentToolExecutor}가 한다.
 *
 * 조회 도구 2개와 "제안" 도구 2개뿐이다. 식품을 바로 바꾸는 도구는 없고,
 * 제안을 실행하는 확인 API는 화면 버튼 전용이라 여기에 넣지 않는다.
 * 모든 도구에 strict를 걸어 스키마에 맞는 입력만 받는다.
 */
public final class AgentTools {

    public static final String FIND_FOODS = "find_foods";
    public static final String GET_FOOD_DETAIL = "get_food_detail";
    public static final String PROPOSE_FINISH = "propose_finish";
    public static final String PROPOSE_DELETE = "propose_delete";

    private AgentTools() {
    }

    public static List<Tool> all() {
        return List.of(findFoods(), getFoodDetail(), proposeFinish(), proposeDelete());
    }

    private static Tool findFoods() {
        return tool(FIND_FOODS,
                "냉장고에서 조건에 맞는 식품을 찾는다. 유통기한이 가까운 순서로 최대 50개를 돌려준다. "
                        + "식품에 대해 답하거나 제안하기 전에 반드시 이 도구로 실제 식품과 id를 확인한다.",
                Map.of(
                        "keyword", Map.of("type", "string",
                                "description", "식품 이름 일부 (예: 우유). 이름에 이 글자가 들어간 식품을 찾는다"),
                        "category", Map.of("type", "string",
                                "enum", List.of("유제품", "가공식품", "채소", "과일", "고기", "음료", "기타"),
                                "description", "사용자가 종류를 직접 말했을 때만 넣는다 (예: '채소 뭐 있어?'). "
                                        + "식품 이름으로 찾을 때는 넣지 않는다. 사용자가 다른 카테고리로 등록했을 수 있다"),
                        "status", Map.of("type", "string",
                                "enum", List.of("ALL", "EXPIRED", "TODAY", "SOON"),
                                "description", "ALL: 전부, EXPIRED: 유통기한 지남, TODAY: 오늘까지, SOON: 3일 안에 끝남"),
                        "scope", Map.of("type", "string",
                                "enum", List.of("VISIBLE", "MINE", "SHARED"),
                                "description", "VISIBLE: 볼 수 있는 전부, MINE: 내 개인(비공유) 식품, SHARED: 공유 식품")),
                List.of());
    }

    private static Tool getFoodDetail() {
        return tool(GET_FOOD_DETAIL,
                "식품 하나의 자세한 정보(메모, 등록일 포함)를 본다. foodId는 find_foods 결과의 id를 쓴다.",
                Map.of("foodId", Map.of("type", "integer")),
                List.of("foodId"));
    }

    private static Tool proposeFinish() {
        return tool(PROPOSE_FINISH,
                "다 먹었거나 버린 식품을 정리하자고 제안한다. 실행되지 않고 사용자 화면에 확인 카드가 뜬다. "
                        + "사용자가 버튼을 눌러야 처리되고 리포트에 기록된다. 한 번에 최대 50개.",
                Map.of(
                        "foodIds", Map.of("type", "array", "items", Map.of("type", "integer"),
                                "description", "find_foods 결과의 식품 id 목록"),
                        "result", Map.of("type", "string", "enum", List.of("소비", "폐기"),
                                "description", "소비: 다 먹음, 폐기: 버림")),
                List.of("foodIds", "result"));
    }

    private static Tool proposeDelete() {
        return tool(PROPOSE_DELETE,
                "잘못 등록한 식품 1개를 기록 없이 지우자고 제안한다. 사용자가 잘못 등록했다고 분명히 말했을 때만 쓴다. "
                        + "먹었거나 버린 식품에는 쓰지 않는다 (propose_finish 사용). 실행되지 않고 확인 카드가 뜬다.",
                Map.of("foodId", Map.of("type", "integer")),
                List.of("foodId"));
    }

    private static Tool tool(String name, String description, Map<String, Object> properties, List<String> required) {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        properties.forEach((key, schema) -> props.putAdditionalProperty(key, JsonValue.from(schema)));

        return Tool.builder()
                .name(name)
                .description(description)
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(props.build())
                        .required(required)
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }
}
