package com.be16_2nd.SmartFridge.recipe.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 냉장고 식품과 레시피 재료가 겹치는 정도로 레시피를 추천한다 (AI 없이 동작하는 1차 추천).
 * 식품 이름은 재료 사전으로 대표 이름을 맞춘 뒤 재료 마스터와 비교하고, 기본 양념은 세지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RecipeRecommendService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    // 이 기간 안에 유통기한이 끝나는 재료를 먼저 쓰는 레시피를 앞에 둔다
    static final int EXPIRING_DAYS = 3;
    // IN 조건에 빈 목록을 넘기지 않으려고 쓰는 없는 id
    private static final Long NO_ID = -1L;

    private final FridgeAccessValidator fridgeAccessValidator;
    private final FoodRepository foodRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final RecipeRepository recipeRepository;
    private final IngredientDictionary dictionary;

    public List<RecipeRecommendResDto> recommend(Long fridgeId, Integer size) {
        int limit = normalizeSize(size);
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);

        OwnedIngredients owned = findOwnedIngredients(fridgeId, context);
        if (owned.ownedIds().isEmpty()) {
            return List.of();
        }
        Set<Long> ownedIds = owned.ownedIds();
        Set<Long> expiringIds = owned.expiringIds();

        // 겹치는 레시피를 추천 순서대로 가져와 재료 목록을 붙인다
        List<RecipeMatch> matches = recipeIngredientRepository.findBestMatches(
                ownedIds, expiringIds.isEmpty() ? Set.of(NO_ID) : expiringIds, Limit.of(limit));
        if (matches.isEmpty()) {
            return List.of();
        }
        List<Long> recipeIds = matches.stream().map(RecipeMatch::recipeId).toList();
        Map<Long, Recipe> recipes = recipeRepository.findAllById(recipeIds).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity()));
        Map<Long, List<RecipeIngredient>> ingredientsByRecipe = new LinkedHashMap<>();
        for (RecipeIngredient ri : recipeIngredientRepository.findAllWithIngredientByRecipeIdIn(recipeIds)) {
            ingredientsByRecipe.computeIfAbsent(ri.getRecipe().getId(), id -> new ArrayList<>()).add(ri);
        }

        return matches.stream()
                .map(match -> toDto(match, recipes.get(match.recipeId()),
                        ingredientsByRecipe.getOrDefault(match.recipeId(), List.of()), ownedIds, expiringIds))
                .toList();
    }

    /**
     * 레시피 상세. 재료마다 이 냉장고에 있는지, 유통기한이 임박했는지를 붙인다.
     * 냉장고 멤버만 볼 수 있다 (재료 보유 여부가 그 냉장고 기준이라서).
     */
    public RecipeDetailResDto detail(Long fridgeId, Long recipeId) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new EntityNotFoundException("레시피를 찾을 수 없습니다."));
        OwnedIngredients owned = findOwnedIngredients(fridgeId, context);

        List<RecipeDetailResDto.IngredientItem> items = new ArrayList<>();
        long matchedCount = 0;
        long totalCount = 0;
        for (RecipeIngredient ri : recipeIngredientRepository.findAllWithIngredientByRecipeIdIn(List.of(recipeId))) {
            Ingredient ingredient = ri.getIngredient();
            boolean pantry = ingredient.isPantry();
            boolean has = !pantry && owned.ownedIds().contains(ingredient.getId());
            boolean expiring = has && owned.expiringIds().contains(ingredient.getId());
            if (!pantry) {
                totalCount++;
                if (has) {
                    matchedCount++;
                }
            }
            items.add(new RecipeDetailResDto.IngredientItem(ingredient.getName(), ri.getAmount(), pantry, has, expiring));
        }

        List<RecipeDetailResDto.Step> steps = recipe.getSteps().stream()
                .map(step -> new RecipeDetailResDto.Step(step.getStepOrder(), step.getDescription(), step.getImageUrl()))
                .toList();

        return new RecipeDetailResDto(
                recipe.getId(),
                recipe.getName(),
                recipe.getDishType(),
                recipe.getCookingMethod(),
                recipe.getMainImageUrl(),
                recipe.getThumbnailUrl(),
                recipe.getCalories(),
                recipe.getTip(),
                matchedCount,
                totalCount,
                items,
                steps);
    }

    /**
     * 냉장고 식품을 재료 마스터의 재료로 바꾼다 (기본 양념과 레시피에 한 번도 나오지 않은 식품은 빠진다).
     * 같은 재료 중 하나라도 유통기한이 임박하면 임박한 재료로 본다.
     */
    private OwnedIngredients findOwnedIngredients(Long fridgeId, FridgeAccessValidator.FridgeContext context) {
        LocalDateTime now = LocalDateTime.now();
        List<Food> foods = foodRepository.findUsableFoods(fridgeId, context.member(), now);

        LocalDateTime expiringBefore = now.plusDays(EXPIRING_DAYS);
        Map<String, Boolean> expiringByName = new HashMap<>();
        for (Food food : foods) {
            String name = toIngredientName(food.getName());
            if (name.isEmpty()) {
                continue;
            }
            boolean expiring = food.getExpirationDateTime().isBefore(expiringBefore);
            expiringByName.merge(name, expiring, Boolean::logicalOr);
        }
        if (expiringByName.isEmpty()) {
            return new OwnedIngredients(Set.of(), Set.of());
        }

        Set<Long> ownedIds = new HashSet<>();
        Set<Long> expiringIds = new HashSet<>();
        for (Ingredient ingredient : ingredientRepository.findAllByNameIn(expiringByName.keySet())) {
            if (ingredient.isPantry()) {
                continue;
            }
            ownedIds.add(ingredient.getId());
            if (expiringByName.get(ingredient.getName())) {
                expiringIds.add(ingredient.getId());
            }
        }
        return new OwnedIngredients(ownedIds, expiringIds);
    }

    private record OwnedIngredients(Set<Long> ownedIds, Set<Long> expiringIds) {
    }

    /**
     * 식품 이름을 재료 사전의 대표 이름으로 바꾼다. "두부(국산)"처럼 괄호 안 설명은 뗀다.
     */
    String toIngredientName(String foodName) {
        if (foodName == null) {
            return "";
        }
        String stripped = foodName.replaceAll("\\([^)]*\\)?", "").strip();
        if (stripped.isEmpty()) {
            return "";
        }
        return dictionary.resolve(stripped).name();
    }

    static int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("추천 개수는 1에서 " + MAX_SIZE + " 사이로 입력해 주세요.");
        }
        return size;
    }

    private RecipeRecommendResDto toDto(RecipeMatch match, Recipe recipe, List<RecipeIngredient> recipeIngredients,
                                        Set<Long> ownedIds, Set<Long> expiringIds) {
        List<String> matched = new ArrayList<>();
        List<String> expiring = new ArrayList<>();
        List<RecipeRecommendResDto.MissingIngredient> missing = new ArrayList<>();
        for (RecipeIngredient ri : recipeIngredients) {
            Ingredient ingredient = ri.getIngredient();
            if (ingredient.isPantry()) {
                continue;
            }
            if (ownedIds.contains(ingredient.getId())) {
                matched.add(ingredient.getName());
                if (expiringIds.contains(ingredient.getId())) {
                    expiring.add(ingredient.getName());
                }
            } else {
                missing.add(new RecipeRecommendResDto.MissingIngredient(ingredient.getName(), ri.getAmount()));
            }
        }
        return new RecipeRecommendResDto(
                recipe.getId(),
                recipe.getName(),
                recipe.getDishType(),
                recipe.getCookingMethod(),
                recipe.getThumbnailUrl(),
                recipe.getCalories(),
                match.matchedCount(),
                match.totalCount(),
                matched,
                missing,
                expiring);
    }
}
