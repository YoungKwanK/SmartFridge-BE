package com.be16_2nd.SmartFridge.agent.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/**
 * 식품 id 목록을 "12,15,18" 문자열 한 칸에 저장한다.
 * 제안은 만든 뒤 바뀌지 않고 id로 검색할 일도 없어서 별도 테이블 대신 한 칸에 둔다.
 */
@Converter
public class FoodIdListConverter implements AttributeConverter<List<Long>, String> {

    @Override
    public String convertToDatabaseColumn(List<Long> foodIds) {
        if (foodIds == null || foodIds.isEmpty()) {
            return "";
        }
        return String.join(",", foodIds.stream().map(String::valueOf).toList());
    }

    @Override
    public List<Long> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(Long::valueOf).toList();
    }
}
