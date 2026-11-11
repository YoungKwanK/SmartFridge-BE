package com.be16_2nd.SmartFridge.food.repository;

import com.be16_2nd.SmartFridge.food.domain.FoodHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodHistoryRepository extends JpaRepository<FoodHistory, Long> {
}
