package com.be16_2nd.SmartFridge.food.dto;

import com.be16_2nd.SmartFridge.food.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 식품 리포트 응답. 모든 숫자는 기록 건수가 아니라 수량(개수) 합계다.
 * 기록이 없으면 비율·평균은 null, 목록은 빈 목록이다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodReportResDto {
    private ReportPeriod period;
    private ReportScope scope;
    private LocalDate from;          // 기간 시작일 (전체 리포트는 첫 기록 날짜, 기록이 없으면 null)
    private LocalDate to;            // 기간 마지막 날 (포함)

    private Summary summary;
    private List<CategoryStat> byCategory;      // 버린 개수가 많은 순
    private DiscardTiming discardTiming;
    private StorageDays avgStorageDays;
    private List<NameCount> topDiscarded;       // 많이 버린 식품 최대 3개
    private List<MonthStat> monthlyTrend;       // 전체 리포트에만 (월별 추이), 월간 리포트는 빈 목록
    private List<String> insights;              // 화면에 그대로 보여 줄 문장

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int consumedCount;
        private int discardedCount;
        private Double discardRate;      // 폐기율(%) 소수 첫째 자리
        private Double discardRateDiff;  // 지난달 대비 변화(%p). 월간 리포트에서 두 달 모두 기록이 충분할 때만
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStat {
        private Category category;
        private int consumed;
        private int discarded;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscardTiming {
        private int afterExpiration;   // 유통기한이 지난 뒤 버림 (잊어버린 경우)
        private int beforeExpiration;  // 유통기한 전에 버림
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageDays {
        private Double consumed;   // 다 먹은 식품의 평균 보관일
        private Double discarded;  // 버린 식품의 평균 보관일
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NameCount {
        private String name;
        private int count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthStat {
        private String month;        // 2026-11
        private int consumed;
        private int discarded;
        private Double discardRate;
    }
}
