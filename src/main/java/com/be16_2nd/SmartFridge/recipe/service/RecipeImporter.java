package com.be16_2nd.SmartFridge.recipe.service;

import com.be16_2nd.SmartFridge.recipe.domain.Ingredient;
import com.be16_2nd.SmartFridge.recipe.domain.Recipe;
import com.be16_2nd.SmartFridge.recipe.ingredient.IngredientDictionary;
import com.be16_2nd.SmartFridge.recipe.ingredient.IngredientParser;
import com.be16_2nd.SmartFridge.recipe.repository.IngredientRepository;
import com.be16_2nd.SmartFridge.recipe.repository.RecipeRepository;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 받아 온 레시피 한 묶음(페이지)을 DB에 반영한다. 묶음마다 트랜잭션을 따로 둬서
 * 중간 묶음이 실패해도 앞서 넣은 묶음은 남는다.
 */
@Component
@RequiredArgsConstructor
public class RecipeImporter {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientDictionary dictionary;

    @Transactional
    public PageResult importPage(List<RecipeSourceRow> rows) {
        // 1) 재료 문자열을 나누고 사전 기준 이름으로 바꾼다 (레시피 안에서 같은 재료는 처음 것만)
        List<ParsedRecipe> parsed = new ArrayList<>();
        List<String> unknownNames = new ArrayList<>();
        Map<String, Boolean> pantryByName = new LinkedHashMap<>();
        for (RecipeSourceRow row : rows) {
            LinkedHashMap<String, String> amountByName = new LinkedHashMap<>();
            for (IngredientParser.ParsedIngredient item : IngredientParser.parse(row.ingredientsText())) {
                IngredientDictionary.Resolved resolved = dictionary.resolve(item.name());
                if (amountByName.containsKey(resolved.name())) {
                    continue;
                }
                amountByName.put(resolved.name(), item.amount());
                pantryByName.put(resolved.name(), resolved.pantry());
                if (!resolved.known()) {
                    unknownNames.add(resolved.name());
                }
            }
            parsed.add(new ParsedRecipe(row, amountByName));
        }

        // 2) 재료 마스터를 한 번에 찾고 없는 것만 만든다. 사전의 기본 양념 표시도 맞춘다
        Map<String, Ingredient> ingredients = findOrCreateIngredients(pantryByName);

        // 3) 원본 번호로 기존 레시피를 찾아 덮어쓰거나 새로 만든다
        Map<Long, Recipe> existing = recipeRepository.findAllBySourceIdIn(
                        rows.stream().map(RecipeSourceRow::sourceId).toList())
                .stream()
                .collect(Collectors.toMap(Recipe::getSourceId, Function.identity()));

        int created = 0;
        int updated = 0;
        for (ParsedRecipe p : parsed) {
            RecipeSourceRow row = p.row();
            Recipe recipe = existing.get(row.sourceId());
            if (recipe == null) {
                recipe = Recipe.builder().sourceId(row.sourceId()).name(row.name()).build();
                existing.put(row.sourceId(), recipe);   // 같은 묶음 안에 같은 번호가 또 오면 덮어쓰기로
                created++;
            } else {
                updated++;
            }
            recipe.updateInfo(row.name(), row.dishType(), row.cookingMethod(), row.ingredientsText(),
                    row.mainImageUrl(), row.thumbnailUrl(), row.calories(), row.tip());
            recipe.replaceSteps(row.steps());

            LinkedHashMap<Ingredient, String> amounts = new LinkedHashMap<>();
            p.amountByName().forEach((name, amount) -> amounts.put(ingredients.get(name), amount));
            recipe.replaceIngredients(amounts);

            if (recipe.getId() == null) {
                recipeRepository.save(recipe);
            }
        }
        return new PageResult(created, updated, unknownNames);
    }

    private Map<String, Ingredient> findOrCreateIngredients(Map<String, Boolean> pantryByName) {
        Map<String, Ingredient> byName = ingredientRepository.findAllByNameIn(pantryByName.keySet())
                .stream()
                .collect(Collectors.toMap(Ingredient::getName, Function.identity()));

        List<Ingredient> toCreate = new ArrayList<>();
        pantryByName.forEach((name, pantry) -> {
            Ingredient ingredient = byName.get(name);
            if (ingredient == null) {
                toCreate.add(Ingredient.builder().name(name).pantry(pantry).build());
            } else if (ingredient.isPantry() != pantry) {
                ingredient.changePantry(pantry);
            }
        });
        ingredientRepository.saveAll(toCreate).forEach(i -> byName.put(i.getName(), i));
        return byName;
    }

    private record ParsedRecipe(RecipeSourceRow row, LinkedHashMap<String, String> amountByName) {
    }

    /**
     * @param unknownNames 사전에 없는 재료 이름 (레시피마다 한 번씩, 중복 포함)
     */
    public record PageResult(int created, int updated, List<String> unknownNames) {
    }
}
