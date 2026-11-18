package com.be16_2nd.SmartFridge.recipe.repository;

import com.be16_2nd.SmartFridge.recipe.domain.Recipe;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findAllBySourceIdIn(Collection<Long> sourceIds);

    Optional<Recipe> findBySourceId(Long sourceId);

    /**
     * 이름에 검색어가 들어간 레시피. 띄어쓰기는 무시한다 ("김치 찌개"로 "김치찌개"를 찾는다).
     * 이름이 짧을수록 검색어에 가까운 레시피라서 앞에 둔다.
     *
     * @param keyword 띄어쓰기를 빼고 LIKE 특수문자(%, _, !)를 '!'로 이스케이프한 검색어
     */
    @Query("""
            select r from Recipe r
            where replace(r.name, ' ', '') like concat('%', :keyword, '%') escape '!'
            order by length(r.name) asc, r.id asc
            """)
    Slice<Recipe> searchByName(@Param("keyword") String keyword, Pageable pageable);
}
