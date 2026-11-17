package com.be16_2nd.SmartFridge.recipe.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.recipe.domain.Ingredient;
import com.be16_2nd.SmartFridge.recipe.domain.Recipe;
import com.be16_2nd.SmartFridge.recipe.domain.RecipeIngredient;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeDetailResDto;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeRecommendResDto;
import com.be16_2nd.SmartFridge.recipe.ingredient.IngredientDictionary;
import com.be16_2nd.SmartFridge.recipe.repository.IngredientRepository;
import com.be16_2nd.SmartFridge.recipe.repository.RecipeIngredientRepository;
import com.be16_2nd.SmartFridge.recipe.repository.RecipeMatch;
import com.be16_2nd.SmartFridge.recipe.repository.RecipeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 레시피 추천 서비스 단위 테스트 (스프링·DB 없이 가짜 객체로만 확인)
 */
@ExtendWith(MockitoExtension.class)
class RecipeRecommendServiceTest {

    @Mock FridgeAccessValidator fridgeAccessValidator;
    @Mock FoodRepository foodRepository;
    @Mock IngredientRepository ingredientRepository;
    @Mock RecipeIngredientRepository recipeIngredientRepository;
    @Mock RecipeRepository recipeRepository;

    RecipeRecommendService service;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member me = Member.builder().id(UUID.randomUUID()).name("홍길동").build();

    final Ingredient egg = Ingredient.builder().id(10L).name("계란").pantry(false).build();
    final Ingredient greenOnion = Ingredient.builder().id(11L).name("대파").pantry(false).build();
    final Ingredient tofu = Ingredient.builder().id(12L).name("두부").pantry(false).build();
    final Ingredient salt = Ingredient.builder().id(13L).name("소금").pantry(true).build();

    @BeforeEach
    void setUp() {
        IngredientDictionary dictionary = IngredientDictionary.of("""
                @기본양념 소금
                계란 = 달걀, 달걀노른자
                대파 = 파
                """);
        service = new RecipeRecommendService(fridgeAccessValidator, foodRepository, ingredientRepository,
                recipeIngredientRepository, recipeRepository, dictionary);
    }

    @Test
    void 식품_이름을_재료_사전_대표_이름으로_바꾼다() {
        assertThat(service.toIngredientName("달걀")).isEqualTo("계란");
        assertThat(service.toIngredientName("대 파")).isEqualTo("대파");
        assertThat(service.toIngredientName("두부(국산)")).isEqualTo("두부");
        assertThat(service.toIngredientName("Milk")).isEqualTo("milk");
        assertThat(service.toIngredientName("(메모)")).isEmpty();
    }

    @Test
    void 가진_재료와_부족한_재료와_임박한_재료를_나눠서_돌려준다() {
        givenFoods(food("달걀", 1), food("대파", 10), food("소금", 100));
        when(ingredientRepository.findAllByNameIn(anyCollection())).thenReturn(List.of(egg, greenOnion, salt));
        when(recipeIngredientRepository.findBestMatches(anyCollection(), anyCollection(), any(Limit.class)))
                .thenReturn(List.of(new RecipeMatch(100L, 2L, 1L, 3L)));
        Recipe recipe = Recipe.builder().id(100L).sourceId(1L).name("계란찜").dishType("반찬").build();
        when(recipeRepository.findAllById(List.of(100L))).thenReturn(List.of(recipe));
        when(recipeIngredientRepository.findAllWithIngredientByRecipeIdIn(List.of(100L))).thenReturn(List.of(
                ri(recipe, egg, "2개"), ri(recipe, tofu, "50g"), ri(recipe, greenOnion, "10g"), ri(recipe, salt, "약간")));

        List<RecipeRecommendResDto> result = service.recommend(fridgeId, null);

        assertThat(result).hasSize(1);
        RecipeRecommendResDto dto = result.get(0);
        assertThat(dto.name()).isEqualTo("계란찜");
        assertThat(dto.matchedCount()).isEqualTo(2);
        assertThat(dto.totalCount()).isEqualTo(3);
        assertThat(dto.matchedIngredients()).containsExactly("계란", "대파");
        assertThat(dto.missingIngredients())
                .extracting(RecipeRecommendResDto.MissingIngredient::name, RecipeRecommendResDto.MissingIngredient::amount)
                .containsExactly(tuple("두부", "50g"));
        assertThat(dto.expiringIngredients()).containsExactly("계란");

        // 기본 양념(소금)은 가진 재료로 넘기지 않고, 기본 개수로 찾는다
        ArgumentCaptor<Collection<Long>> owned = idsCaptor();
        ArgumentCaptor<Collection<Long>> expiring = idsCaptor();
        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(recipeIngredientRepository).findBestMatches(owned.capture(), expiring.capture(), limit.capture());
        assertThat(owned.getValue()).containsExactlyInAnyOrder(10L, 11L);
        assertThat(expiring.getValue()).containsExactly(10L);
        assertThat(limit.getValue().max()).isEqualTo(RecipeRecommendService.DEFAULT_SIZE);
    }

    @Test
    void 임박한_재료가_없으면_없는_id_하나를_넘긴다() {
        givenFoods(food("대파", 10));
        when(ingredientRepository.findAllByNameIn(anyCollection())).thenReturn(List.of(greenOnion));
        when(recipeIngredientRepository.findBestMatches(anyCollection(), anyCollection(), any(Limit.class)))
                .thenReturn(List.of());

        assertThat(service.recommend(fridgeId, 5)).isEmpty();

        ArgumentCaptor<Collection<Long>> expiring = idsCaptor();
        verify(recipeIngredientRepository).findBestMatches(anyCollection(), expiring.capture(), eq(Limit.of(5)));
        assertThat(expiring.getValue()).containsExactly(-1L);
        verify(recipeRepository, never()).findAllById(any());
    }

    @Test
    void 냉장고가_비었으면_재료를_찾지_않는다() {
        givenFoods();

        assertThat(service.recommend(fridgeId, null)).isEmpty();
        verifyNoInteractions(ingredientRepository, recipeIngredientRepository, recipeRepository);
    }

    @Test
    void 기본_양념만_있으면_레시피를_찾지_않는다() {
        givenFoods(food("소금", 100));
        when(ingredientRepository.findAllByNameIn(anyCollection())).thenReturn(List.of(salt));

        assertThat(service.recommend(fridgeId, null)).isEmpty();
        verifyNoInteractions(recipeIngredientRepository, recipeRepository);
    }

    @Test
    void 추천_개수는_1에서_50_사이다() {
        assertThat(RecipeRecommendService.normalizeSize(null)).isEqualTo(20);
        assertThat(RecipeRecommendService.normalizeSize(50)).isEqualTo(50);
        assertThatThrownBy(() -> service.recommend(fridgeId, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recommend(fridgeId, 51)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(fridgeAccessValidator);
    }

    @Test
    void 상세는_재료마다_있는지와_임박했는지를_붙이고_기본_양념은_세지_않는다() {
        givenFoods(food("달걀", 1), food("대파", 10), food("소금", 100));
        when(ingredientRepository.findAllByNameIn(anyCollection())).thenReturn(List.of(egg, greenOnion, salt));
        Recipe recipe = Recipe.builder().id(100L).sourceId(1L).name("계란찜").tip("물을 조금만").build();
        recipe.replaceSteps(List.of(
                new Recipe.StepContent("계란을 푼다", "https://img/1.jpg"),
                new Recipe.StepContent("찐다", null)));
        when(recipeRepository.findById(100L)).thenReturn(Optional.of(recipe));
        when(recipeIngredientRepository.findAllWithIngredientByRecipeIdIn(List.of(100L))).thenReturn(List.of(
                ri(recipe, egg, "2개"), ri(recipe, tofu, "50g"), ri(recipe, greenOnion, null), ri(recipe, salt, "약간")));

        RecipeDetailResDto dto = service.detail(fridgeId, 100L);

        assertThat(dto.name()).isEqualTo("계란찜");
        assertThat(dto.tip()).isEqualTo("물을 조금만");
        assertThat(dto.matchedCount()).isEqualTo(2);
        assertThat(dto.totalCount()).isEqualTo(3);
        assertThat(dto.ingredients())
                .extracting(RecipeDetailResDto.IngredientItem::name, RecipeDetailResDto.IngredientItem::amount,
                        RecipeDetailResDto.IngredientItem::pantry, RecipeDetailResDto.IngredientItem::owned,
                        RecipeDetailResDto.IngredientItem::expiring)
                .containsExactly(
                        tuple("계란", "2개", false, true, true),
                        tuple("두부", "50g", false, false, false),
                        tuple("대파", null, false, true, false),
                        tuple("소금", "약간", true, false, false));
        assertThat(dto.steps())
                .extracting(RecipeDetailResDto.Step::order, RecipeDetailResDto.Step::description,
                        RecipeDetailResDto.Step::imageUrl)
                .containsExactly(tuple(1, "계란을 푼다", "https://img/1.jpg"), tuple(2, "찐다", null));
    }

    @Test
    void 냉장고가_비어도_상세는_모든_재료를_없는_재료로_돌려준다() {
        givenFoods();
        Recipe recipe = Recipe.builder().id(100L).sourceId(1L).name("계란찜").build();
        when(recipeRepository.findById(100L)).thenReturn(Optional.of(recipe));
        when(recipeIngredientRepository.findAllWithIngredientByRecipeIdIn(List.of(100L)))
                .thenReturn(List.of(ri(recipe, egg, "2개")));

        RecipeDetailResDto dto = service.detail(fridgeId, 100L);

        assertThat(dto.matchedCount()).isZero();
        assertThat(dto.totalCount()).isEqualTo(1);
        assertThat(dto.ingredients()).singleElement()
                .extracting(RecipeDetailResDto.IngredientItem::owned).isEqualTo(false);
        assertThat(dto.steps()).isEmpty();
        verifyNoInteractions(ingredientRepository);
    }

    @Test
    void 없는_레시피를_보면_찾을_수_없다고_알린다() {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, Type.COMMON));
        when(recipeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(fridgeId, 999L)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(foodRepository, recipeIngredientRepository);
    }

    private void givenFoods(Food... foods) {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, Type.COMMON));
        when(foodRepository.findUsableFoods(eq(fridgeId), eq(me), any(LocalDateTime.class))).thenReturn(List.of(foods));
    }

    private Food food(String name, int daysLeft) {
        return Food.builder()
                .name(name)
                .quantity(1)
                .storageStartDate(LocalDate.now())
                .expirationDateTime(LocalDateTime.now().plusDays(daysLeft))
                .isShared(true)
                .member(me)
                .fridge(fridge)
                .build();
    }

    private RecipeIngredient ri(Recipe recipe, Ingredient ingredient, String amount) {
        return RecipeIngredient.builder().recipe(recipe).ingredient(ingredient).amount(amount).build();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Collection<Long>> idsCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }
}
