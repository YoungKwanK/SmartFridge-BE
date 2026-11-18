package com.be16_2nd.SmartFridge.recipe.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.recipe.domain.Ingredient;
import com.be16_2nd.SmartFridge.recipe.domain.Recipe;
import com.be16_2nd.SmartFridge.recipe.domain.RecipeIngredient;
import com.be16_2nd.SmartFridge.recipe.dto.IngredientOptionResDto;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeDetailResDto;
import com.be16_2nd.SmartFridge.recipe.dto.RecipePickResDto;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeRecommendResDto;
import com.be16_2nd.SmartFridge.recipe.dto.RecipeSearchResDto;
import com.be16_2nd.SmartFridge.recipe.ingredient.IngredientDictionary;
import com.be16_2nd.SmartFridge.recipe.repository.IngredientRepository;
import com.be16_2nd.SmartFridge.recipe.repository.RecipeIngredientRepository;
import com.be16_2nd.SmartFridge.recipe.repository.RecipeMatch;
import com.be16_2nd.SmartFridge.recipe.repository.RecipePickMatch;
import com.be16_2nd.SmartFridge.recipe.repository.RecipeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    // 재료 검색 결과 최대 개수
    static final int INGREDIENT_OPTION_SIZE = 20;
    // 한 번에 고를 수 있는 재료 수
    public static final int MAX_SELECTED_INGREDIENTS = 10;
    static final int MAX_KEYWORD_LENGTH = 50;
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
        Map<Long, List<RecipeIngredient>> ingredientsByRecipe = findIngredientsByRecipe(recipeIds);

        return recipeIds.stream()
                .map(id -> toDto(recipes.get(id), ingredientsByRecipe.getOrDefault(id, List.of()), owned))
                .toList();
    }

    /**
     * 레시피 이름 검색. 띄어쓰기는 무시하고, 이름이 짧은(검색어에 가까운) 순으로 돌려준다.
     * 재료마다 이 냉장고에 있는지를 붙여서 추천 목록과 같은 모양으로 보여줄 수 있게 한다.
     */
    public RecipeSearchResDto search(Long fridgeId, String keyword, Integer page, Integer size) {
        String pattern = toLikeKeyword(keyword);
        int limit = normalizeSize(size);
        int pageNo = normalizePage(page);
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);

        Slice<Recipe> slice = recipeRepository.searchByName(pattern, PageRequest.of(pageNo, limit));
        if (slice.isEmpty()) {
            return new RecipeSearchResDto(List.of(), false);
        }
        OwnedIngredients owned = findOwnedIngredients(fridgeId, context);
        List<Long> recipeIds = slice.getContent().stream().map(Recipe::getId).toList();
        Map<Long, List<RecipeIngredient>> ingredientsByRecipe = findIngredientsByRecipe(recipeIds);

        List<RecipeRecommendResDto> recipes = slice.getContent().stream()
                .map(recipe -> toDto(recipe, ingredientsByRecipe.getOrDefault(recipe.getId(), List.of()), owned))
                .toList();
        return new RecipeSearchResDto(recipes, slice.hasNext());
    }

    /**
     * 레시피 추천에 고를 재료를 찾는다 (기본 양념은 빠진다).
     * 검색어가 없으면 이 냉장고에 있는 재료를, 있으면 이름에 검색어가 들어간 재료를 냉장고에 없는 것까지 돌려준다.
     * "달걀"처럼 재료 사전의 별칭으로 찾아도 대표 이름("계란")이 맨 앞에 나온다.
     */
    public List<IngredientOptionResDto> searchIngredients(Long fridgeId, String keyword) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        OwnedIngredients owned = findOwnedIngredients(fridgeId, context);

        List<Ingredient> ingredients;
        if (keyword == null || keyword.isBlank()) {
            if (owned.ownedIds().isEmpty()) {
                return List.of();
            }
            ingredients = ingredientRepository.findAllById(owned.ownedIds()).stream()
                    .sorted(Comparator.comparing(Ingredient::getName))
                    .toList();
        } else {
            String pattern = toLikeKeyword(keyword);
            Map<Long, Ingredient> found = new LinkedHashMap<>();
            IngredientDictionary.Resolved resolved = dictionary.resolve(keyword.strip());
            if (resolved.known() && !resolved.pantry()) {
                ingredientRepository.findAllByNameIn(List.of(resolved.name()))
                        .forEach(ingredient -> found.put(ingredient.getId(), ingredient));
            }
            ingredientRepository.searchByName(pattern, Limit.of(INGREDIENT_OPTION_SIZE))
                    .forEach(ingredient -> found.putIfAbsent(ingredient.getId(), ingredient));
            ingredients = found.values().stream().limit(INGREDIENT_OPTION_SIZE).toList();
        }

        return ingredients.stream()
                .map(ingredient -> new IngredientOptionResDto(
                        ingredient.getId(),
                        ingredient.getName(),
                        owned.ownedIds().contains(ingredient.getId()),
                        owned.expiringIds().contains(ingredient.getId())))
                .toList();
    }

    /**
     * 고른 재료로 레시피를 추천한다. 냉장고에 없는 재료도 고를 수 있다 (사 올 재료로 무엇을 만들지 볼 때).
     * 고른 재료를 많이 쓰는 레시피가 앞에 오고, 그다음은 고른 재료와 냉장고 재료 말고 더 필요한 재료가 적은 순이다.
     * 기본 양념이나 없는 재료 id 는 무시한다.
     */
    public List<RecipePickResDto> recommendByIngredients(Long fridgeId, List<Long> ingredientIds, Integer size) {
        int limit = normalizeSize(size);
        Set<Long> requestedIds = normalizeSelectedIds(ingredientIds);
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);

        Set<Long> selectedIds = ingredientRepository.findAllById(requestedIds).stream()
                .filter(ingredient -> !ingredient.isPantry())
                .map(Ingredient::getId)
                .collect(Collectors.toSet());
        if (selectedIds.isEmpty()) {
            return List.of();
        }
        OwnedIngredients owned = findOwnedIngredients(fridgeId, context);
        Set<Long> availableIds = new HashSet<>(owned.ownedIds());
        availableIds.addAll(selectedIds);

        List<RecipePickMatch> matches = recipeIngredientRepository.findBySelectedIngredients(
                selectedIds, availableIds, Limit.of(limit));
        if (matches.isEmpty()) {
            return List.of();
        }
        List<Long> recipeIds = matches.stream().map(RecipePickMatch::recipeId).toList();
        Map<Long, Recipe> recipes = recipeRepository.findAllById(recipeIds).stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity()));
        Map<Long, List<RecipeIngredient>> ingredientsByRecipe = findIngredientsByRecipe(recipeIds);

        return recipeIds.stream()
                .map(id -> {
                    List<RecipeIngredient> recipeIngredients = ingredientsByRecipe.getOrDefault(id, List.of());
                    List<String> selectedNames = recipeIngredients.stream()
                            .map(RecipeIngredient::getIngredient)
                            .filter(ingredient -> selectedIds.contains(ingredient.getId()))
                            .map(Ingredient::getName)
                            .toList();
                    return new RecipePickResDto(toDto(recipes.get(id), recipeIngredients, owned), selectedNames);
                })
                .toList();
    }

    private Map<Long, List<RecipeIngredient>> findIngredientsByRecipe(List<Long> recipeIds) {
        Map<Long, List<RecipeIngredient>> ingredientsByRecipe = new LinkedHashMap<>();
        for (RecipeIngredient ri : recipeIngredientRepository.findAllWithIngredientByRecipeIdIn(recipeIds)) {
            ingredientsByRecipe.computeIfAbsent(ri.getRecipe().getId(), id -> new ArrayList<>()).add(ri);
        }
        return ingredientsByRecipe;
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

    static int normalizePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상으로 입력해 주세요.");
        }
        return page;
    }

    /**
     * 검색어를 LIKE 에 넣을 모양으로 바꾼다. 띄어쓰기를 빼고, %·_ 가 와일드카드로 쓰이지 않게 '!'로 이스케이프한다.
     */
    static String toLikeKeyword(String keyword) {
        String compact = keyword == null ? "" : keyword.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            throw new IllegalArgumentException("검색어를 입력해 주세요.");
        }
        if (compact.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("검색어는 " + MAX_KEYWORD_LENGTH + "자 이하로 입력해 주세요.");
        }
        return compact.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static Set<Long> normalizeSelectedIds(List<Long> ingredientIds) {
        Set<Long> ids = ingredientIds == null ? Set.of()
                : ingredientIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("재료를 하나 이상 골라 주세요.");
        }
        if (ids.size() > MAX_SELECTED_INGREDIENTS) {
            throw new IllegalArgumentException("재료는 " + MAX_SELECTED_INGREDIENTS + "개까지 고를 수 있습니다.");
        }
        return ids;
    }

    private RecipeRecommendResDto toDto(Recipe recipe, List<RecipeIngredient> recipeIngredients, OwnedIngredients owned) {
        Set<Long> ownedIds = owned.ownedIds();
        Set<Long> expiringIds = owned.expiringIds();
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
                matched.size(),
                matched.size() + missing.size(),
                matched,
                missing,
                expiring);
    }
}
