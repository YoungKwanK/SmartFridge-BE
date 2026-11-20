package com.be16_2nd.SmartFridge.recipe.repository;

import com.be16_2nd.SmartFridge.recipe.domain.Ingredient;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    List<Ingredient> findAllByNameIn(Collection<String> names);

    /**
     * 이름에 검색어가 들어간 재료 (기본 양념은 뺀다). 짧은 이름이 검색어에 가까우니 앞에 둔다.
     *
     * @param keyword 띄어쓰기를 빼고 LIKE 특수문자(%, _, !)를 '!'로 이스케이프한 검색어
     */
    @Query("""
            select i from Ingredient i
            where i.pantry = false
              and i.name like concat('%', :keyword, '%') escape '!'
            order by length(i.name) asc, i.name asc
            """)
    List<Ingredient> searchByName(@Param("keyword") String keyword, Limit limit);
}
