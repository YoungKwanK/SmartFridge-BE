package com.be16_2nd.SmartFridge.recipe.service;

import com.be16_2nd.SmartFridge.common.ai.AiClient;
import com.be16_2nd.SmartFridge.common.ai.AiEffort;
import com.be16_2nd.SmartFridge.common.ai.AiException;
import com.be16_2nd.SmartFridge.common.ai.AiPrompt;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeAiRecommendResDto;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeRecommendResDto;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 재료 겹침으로 고른 레시피 후보를 AI(Claude)가 다시 골라 순서를 정하고 추천 이유를 붙인다.
 *
 * <ul>
 *   <li>후보는 {@link RecipeRecommendService} 가 고른 상위 {@value #CANDIDATE_SIZE}개. AI는 이 안에서만 고른다</li>
 *   <li>후보에 없는 id·중복 id는 버리고, AI가 덜 골랐으면 남은 후보를 재료 겹침 순서로 채운다</li>
 *   <li>AI 호출이 실패하면 재료 겹침 순서를 그대로 내려준다 (aiRanked=false)</li>
 *   <li>DB 커넥션을 잡은 채로 AI 응답을 기다리지 않도록 트랜잭션을 걸지 않는다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeAiRecommendService {

    public static final int DEFAULT_SIZE = 5;
    public static final int MAX_SIZE = 10;
    static final int CANDIDATE_SIZE = 20;
    static final int MAX_REASON_LENGTH = 100;
    // 레시피 10개 × 이유 한 줄이면 1,000 토큰 안쪽이다. 넉넉히 잡되 폭주는 막는다
    private static final long MAX_TOKENS = 4000L;

    static final String SYSTEM_PROMPT = """
            너는 냉장고 재료로 오늘 해 먹을 한국 가정식 레시피를 골라 주는 도우미야.
            <candidates> 안의 레시피 후보 중에서 요청한 개수만큼 골라 추천 순서대로 정렬하고, 레시피마다 추천 이유를 한 문장으로 써.

            고르는 기준 (중요한 순서대로):
            1. 유통기한이 임박한 재료(expiring)를 쓰는 레시피를 먼저 둔다. 임박 재료를 여러 개 쓸수록 앞에 둔다
            2. 더 사야 하는 재료(missing)가 적은 레시피를 앞에 둔다
            3. 국·반찬·일품 등 요리 종류와 조리 방법이 한쪽으로 몰리지 않게 섞는다
            4. 이름만 조금 다른 거의 같은 요리는 하나만 고른다

            추천 이유 작성 규칙:
            - 한국어 존댓말 한 문장, 60자 이내
            - 후보 정보에 있는 재료 이름만 쓰고, 없는 재료나 조리법을 지어내지 않는다
            - 임박 재료가 있으면 그 재료를 먼저 언급한다 (예: "유통기한이 임박한 두부와 대파를 한 번에 쓸 수 있어요.")

            recipeId 는 반드시 후보에 있는 id 만 쓰고, 같은 레시피를 두 번 고르지 않는다.
            """;

    private final RecipeRecommendService recipeRecommendService;
    private final AiClient aiClient;

    /** AI가 돌려줄 형식. 이 구조대로 JSON 스키마를 만들어 그 형식으로만 답하게 한다 */
    record AiRanking(
            @JsonPropertyDescription("추천 순서대로 정렬한 레시피")
            List<AiPick> picks
    ) {
    }

    record AiPick(
            @JsonPropertyDescription("후보 목록에 있는 레시피 id")
            long recipeId,
            @JsonPropertyDescription("추천 이유 한 문장")
            String reason
    ) {
    }

    public RecipeAiRecommendResDto recommend(Long fridgeId, Integer size) {
        int limit = normalizeSize(size);
        List<RecipeRecommendResDto> candidates = recipeRecommendService.recommend(fridgeId, CANDIDATE_SIZE);
        if (candidates.isEmpty()) {
            return new RecipeAiRecommendResDto(false, List.of());
        }

        AiRanking ranking;
        try {
            ranking = aiClient.generate(AiPrompt.builder()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(candidates, limit))
                    .effort(AiEffort.LOW)
                    .maxTokens(MAX_TOKENS)
                    .build(), AiRanking.class);
        } catch (AiException e) {
            log.warn("레시피 AI 재랭킹 실패, 재료 겹침 순서로 대체: {}", e.getMessage());
            return fallback(candidates, limit);
        }
        return merge(candidates, ranking, limit);
    }

    static int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("AI 추천 개수는 1에서 " + MAX_SIZE + " 사이로 입력해 주세요.");
        }
        return size;
    }

    /**
     * 후보 정보를 한 줄씩 넘긴다. 재료 이름은 사용자가 입력한 식품명이 아니라 재료 마스터의 대표 이름이다.
     */
    static String buildUserPrompt(List<RecipeRecommendResDto> candidates, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("<candidates>\n");
        for (RecipeRecommendResDto c : candidates) {
            sb.append("- id=").append(c.recipeId())
                    .append(" | name=").append(c.name())
                    .append(" | dishType=").append(nullToDash(c.dishType()))
                    .append(" | method=").append(nullToDash(c.cookingMethod()))
                    .append(" | owned=").append(String.join(", ", c.matchedIngredients()))
                    .append(" | expiring=").append(joinOrDash(c.expiringIngredients()))
                    .append(" | missing=").append(joinOrDash(c.missingIngredients().stream()
                            .map(RecipeRecommendResDto.MissingIngredient::name).toList()))
                    .append('\n');
        }
        sb.append("</candidates>\n\n");
        sb.append("위 후보 중에서 ").append(Math.min(limit, candidates.size())).append("개를 골라 주세요.");
        return sb.toString();
    }

    /**
     * AI가 고른 순서를 따르되, 후보에 없는 id·중복 id는 버리고 모자라면 재료 겹침 순서로 채운다.
     */
    static RecipeAiRecommendResDto merge(List<RecipeRecommendResDto> candidates, AiRanking ranking, int limit) {
        Map<Long, RecipeRecommendResDto> remaining = candidates.stream()
                .collect(Collectors.toMap(RecipeRecommendResDto::recipeId, c -> c, (a, b) -> a, LinkedHashMap::new));
        int target = Math.min(limit, candidates.size());

        List<RecipeAiRecommendResDto.Item> items = new ArrayList<>();
        List<AiPick> picks = ranking == null || ranking.picks() == null ? List.of() : ranking.picks();
        for (AiPick pick : picks) {
            if (items.size() >= target) {
                break;
            }
            RecipeRecommendResDto recipe = remaining.remove(pick.recipeId());
            if (recipe == null) {
                log.debug("AI가 후보에 없거나 이미 고른 레시피를 골라 버림 recipeId={}", pick.recipeId());
                continue;
            }
            items.add(new RecipeAiRecommendResDto.Item(recipe, cleanReason(pick.reason())));
        }
        if (items.isEmpty()) {
            log.warn("AI가 쓸 수 있는 레시피를 하나도 고르지 않아 재료 겹침 순서로 대체");
            return fallback(candidates, limit);
        }
        for (RecipeRecommendResDto recipe : remaining.values()) {
            if (items.size() >= target) {
                break;
            }
            items.add(new RecipeAiRecommendResDto.Item(recipe, null));
        }
        return new RecipeAiRecommendResDto(true, items);
    }

    private static RecipeAiRecommendResDto fallback(List<RecipeRecommendResDto> candidates, int limit) {
        List<RecipeAiRecommendResDto.Item> items = candidates.stream()
                .limit(limit)
                .map(c -> new RecipeAiRecommendResDto.Item(c, null))
                .toList();
        return new RecipeAiRecommendResDto(false, items);
    }

    private static String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String stripped = reason.strip();
        return stripped.length() > MAX_REASON_LENGTH ? stripped.substring(0, MAX_REASON_LENGTH) : stripped;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String joinOrDash(List<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
    }
}
