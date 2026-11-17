package com.be16_2nd.SmartFridge.food.repository;

import com.be16_2nd.SmartFridge.food.domain.FoodHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FoodHistoryRepository extends JpaRepository<FoodHistory, Long> {

    /**
     * 리포트용 기록 조회. 처리 시각이 [from, to) 사이인 기록을 가져온다.
     * memberId가 null이면 냉장고 전체, 있으면 그 회원이 등록한 식품의 기록만 가져온다.
     * (fridge_id, ended_at) 인덱스를 탄다.
     */
    @Query("SELECT h FROM FoodHistory h " +
            "WHERE h.fridgeId = :fridgeId " +
            "AND (:memberId IS NULL OR h.memberId = :memberId) " +
            "AND h.endedAt >= :from AND h.endedAt < :to")
    List<FoodHistory> findForReport(@Param("fridgeId") Long fridgeId,
                                    @Param("memberId") UUID memberId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);
}
