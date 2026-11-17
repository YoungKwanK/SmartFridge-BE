package com.be16_2nd.SmartFridge.recipe.service;

import com.be16_2nd.SmartFridge.common.ai.AiClient;
import com.be16_2nd.SmartFridge.common.ai.AiEffort;
import com.be16_2nd.SmartFridge.common.ai.AiException;
import com.be16_2nd.SmartFridge.common.ai.AiPrompt;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeAiRecommendResDto;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeRecommendResDto;
import com.be16_2nd.SmartFridge.recipe.service.RecipeAiRecommendService.AiPick;
import com.be16_2nd.SmartFridge.recipe.service.RecipeAiRecommendService.AiRanking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 레시피 AI 재랭킹 단위 테스트 (AI 응답은 가짜로 만든다)
 */
@ExtendWith(MockitoExtension.class)
class RecipeAiRecommendServiceTest {

    @Mock RecipeRecommendService recipeRecommendService;
    @Mock AiClient aiClient;

    RecipeAiRecommendService service;

    final Long fridgeId = 1L;
    final RecipeRecommendResDto steamedEgg = recipe(100L, "계란찜", List.of("계란"), List.of(), List.of("두부"));
    final RecipeRecommendResDto tofuStew = recipe(101L, "두부찌개", List.of("두부", "대파"), List.of("두부"), List.of());
    final RecipeRecommendResDto rolledEgg = recipe(102L, "계란말이", List.of("계란", "대파"), List.of(), List.of("당근"));

    @BeforeEach
    void setUp() {
        service = new RecipeAiRecommendService(recipeRecommendService, aiClient);
    }

    @Test
    void AI가_고른_순서와_이유를_그대로_돌려준다() {
        givenCandidates(steamedEgg, tofuStew, rolledEgg);
        givenAi(new AiRanking(List.of(
                new AiPick(101L, "유통기한이 임박한 두부를 쓸 수 있어요."),
                new AiPick(102L, "  가진 계란과 대파로 바로 만들 수 있어요.  "))));

        RecipeAiRecommendResDto result = service.recommend(fridgeId, 2);

        assertThat(result.aiRanked()).isTrue();
        assertThat(result.recipes())
                .extracting(item -> item.recipe().recipeId(), RecipeAiRecommendResDto.Item::reason)
                .containsExactly(
                        tuple(101L, "유통기한이 임박한 두부를 쓸 수 있어요."),
                        tuple(102L, "가진 계란과 대파로 바로 만들 수 있어요."));
    }

    @Test
    void 후보를_20개_가져와_가벼운_effort로_AI에_넘긴다() {
        givenCandidates(steamedEgg, tofuStew);
        givenAi(new AiRanking(List.of(new AiPick(100L, "이유"))));
        ArgumentCaptor<AiPrompt> prompt = ArgumentCaptor.forClass(AiPrompt.class);

        service.recommend(fridgeId, null);

        verify(recipeRecommendService).recommend(fridgeId, RecipeAiRecommendService.CANDIDATE_SIZE);
        verify(aiClient).generate(prompt.capture(), eq(AiRanking.class));
        AiPrompt sent = prompt.getValue();
        assertThat(sent.system()).isEqualTo(RecipeAiRecommendService.SYSTEM_PROMPT);
        assertThat(sent.effort()).isEqualTo(AiEffort.LOW);
        assertThat(sent.user())
                .contains("id=100 | name=계란찜", "missingCount=1 | missing=두부")
                .contains("id=101 | name=두부찌개", "expiring=두부", "missingCount=0 | missing=-")
                // 후보가 기본 개수(5)보다 적으면 후보 수만큼만 고르라고 한다
                .contains("2개를 골라 주세요");
    }

    @Test
    void 후보에_없는_id와_중복_id는_버리고_모자라면_겹침_순서로_채운다() {
        givenCandidates(steamedEgg, tofuStew, rolledEgg);
        givenAi(new AiRanking(List.of(
                new AiPick(999L, "없는 레시피"),
                new AiPick(102L, "계란말이 추천"),
                new AiPick(102L, "또 계란말이"))));

        RecipeAiRecommendResDto result = service.recommend(fridgeId, 3);

        assertThat(result.aiRanked()).isTrue();
        assertThat(result.recipes())
                .extracting(item -> item.recipe().recipeId(), RecipeAiRecommendResDto.Item::reason)
                .containsExactly(
                        tuple(102L, "계란말이 추천"),
                        tuple(100L, null),
                        tuple(101L, null));
    }

    @Test
    void AI가_요청보다_많이_골라도_요청한_개수만_돌려준다() {
        givenCandidates(steamedEgg, tofuStew, rolledEgg);
        givenAi(new AiRanking(List.of(
                new AiPick(102L, "a"), new AiPick(101L, "b"), new AiPick(100L, "c"))));

        assertThat(service.recommend(fridgeId, 1).recipes())
                .extracting(item -> item.recipe().recipeId())
                .containsExactly(102L);
    }

    @Test
    void 이유가_너무_길면_자르고_비어_있으면_null() {
        givenCandidates(steamedEgg, tofuStew);
        givenAi(new AiRanking(List.of(
                new AiPick(100L, "가".repeat(300)),
                new AiPick(101L, "   "))));

        List<RecipeAiRecommendResDto.Item> items = service.recommend(fridgeId, 2).recipes();

        assertThat(items.get(0).reason()).hasSize(RecipeAiRecommendService.MAX_REASON_LENGTH);
        assertThat(items.get(1).reason()).isNull();
    }

    @Test
    void AI_호출이_실패하면_겹침_순서로_대체한다() {
        givenCandidates(steamedEgg, tofuStew, rolledEgg);
        when(aiClient.generate(any(AiPrompt.class), eq(AiRanking.class)))
                .thenThrow(new AiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 요청이 많아요"));

        RecipeAiRecommendResDto result = service.recommend(fridgeId, 2);

        assertThat(result.aiRanked()).isFalse();
        assertThat(result.recipes())
                .extracting(item -> item.recipe().recipeId(), RecipeAiRecommendResDto.Item::reason)
                .containsExactly(tuple(100L, null), tuple(101L, null));
    }

    @Test
    void AI가_쓸_수_있는_레시피를_하나도_고르지_않으면_겹침_순서로_대체한다() {
        givenCandidates(steamedEgg, tofuStew);
        givenAi(new AiRanking(List.of(new AiPick(999L, "없는 레시피"))));

        RecipeAiRecommendResDto result = service.recommend(fridgeId, 2);

        assertThat(result.aiRanked()).isFalse();
        assertThat(result.recipes()).extracting(item -> item.recipe().recipeId()).containsExactly(100L, 101L);
    }

    @Test
    void 후보가_없으면_AI를_부르지_않는다() {
        givenCandidates();

        RecipeAiRecommendResDto result = service.recommend(fridgeId, null);

        assertThat(result.aiRanked()).isFalse();
        assertThat(result.recipes()).isEmpty();
        verifyNoInteractions(aiClient);
    }

    @Test
    void 추천_개수는_1에서_10_사이다() {
        assertThat(RecipeAiRecommendService.normalizeSize(null)).isEqualTo(5);
        assertThat(RecipeAiRecommendService.normalizeSize(10)).isEqualTo(10);
        assertThatThrownBy(() -> service.recommend(fridgeId, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recommend(fridgeId, 11)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(recipeRecommendService, aiClient);
    }

    private void givenCandidates(RecipeRecommendResDto... candidates) {
        when(recipeRecommendService.recommend(fridgeId, RecipeAiRecommendService.CANDIDATE_SIZE))
                .thenReturn(List.of(candidates));
    }

    private void givenAi(AiRanking ranking) {
        when(aiClient.generate(any(AiPrompt.class), eq(AiRanking.class))).thenReturn(ranking);
    }

    private static RecipeRecommendResDto recipe(Long id, String name, List<String> matched, List<String> expiring,
                                                List<String> missing) {
        return new RecipeRecommendResDto(id, name, "반찬", "찌기", null, 100,
                matched.size(), matched.size() + missing.size(), matched,
                missing.stream().map(m -> new RecipeRecommendResDto.MissingIngredient(m, "1개")).toList(),
                expiring);
    }
}
