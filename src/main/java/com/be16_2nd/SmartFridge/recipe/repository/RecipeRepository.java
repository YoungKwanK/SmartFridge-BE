package com.be16_2nd.SmartFridge.recipe.repository;

import com.be16_2nd.SmartFridge.recipe.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findAllBySourceIdIn(Collection<Long> sourceIds);

    Optional<Recipe> findBySourceId(Long sourceId);
}
