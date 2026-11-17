package com.be16_2nd.SmartFridge.recipe.repository;

import com.be16_2nd.SmartFridge.recipe.domain.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    List<Ingredient> findAllByNameIn(Collection<String> names);
}
