package com.be16_2nd.SmartFridge.food.domain;

/**
 * 식품이 냉장고에서 나간 이유.
 * 잘못 등록한 식품은 기록하지 않고 그냥 삭제하므로 여기에 없다.
 */
public enum FoodResult {
    소비,
    폐기
}
