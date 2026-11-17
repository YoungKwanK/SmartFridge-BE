package com.be16_2nd.SmartFridge.food.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.FoodHistory;
import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import com.be16_2nd.SmartFridge.food.dto.FoodReportResDto;
import com.be16_2nd.SmartFridge.food.dto.FoodReportResDto.*;
import com.be16_2nd.SmartFridge.food.dto.ReportPeriod;
import com.be16_2nd.SmartFridge.food.dto.ReportScope;
import com.be16_2nd.SmartFridge.food.repository.FoodHistoryRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 식품 리포트 (월간 / 전체).
 * food_history 기록을 가져와 Java에서 집계한다. 냉장고 하나의 기록은 한 달에 많아야 수백 건이라 따로 집계 테이블을 두지 않는다.
 * 모든 숫자는 기록 건수가 아니라 수량 합계다. 잘못 등록으로 삭제한 식품은 기록이 없으므로 빠진다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FoodReportService {

    // 비교 문장(지난달 대비, 처음 대비)은 두 기간 모두 이 개수 이상일 때만 만든다. 1개 → 2개를 "100% 늘었어요"라고 하지 않으려고
    static final int MIN_COUNT_FOR_COMPARE = 5;
    // 카테고리·보관일 문장은 버린 개수가 이 이상일 때만 만든다
    static final int MIN_DISCARDED_FOR_INSIGHT = 2;
    private static final int TOP_DISCARDED_SIZE = 3;
    // 전체 리포트 조회 시작 시각 (서비스 시작 전)
    private static final LocalDateTime BEGINNING = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final FoodHistoryRepository foodHistoryRepository;
    private final FridgeAccessValidator fridgeAccessValidator;

    /**
     * @param month 월간 리포트의 달 (예: 2026-11). 비우면 이번 달. 전체 리포트에서는 쓰지 않는다.
     */
    public FoodReportResDto getReport(Long fridgeId, ReportPeriod period, ReportScope scope, String month) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        // 냉장고 전체 리포트에는 다른 멤버의 비공유 식품 기록도 들어가므로 관리자만 본다 (식품 목록과 같은 공개 범위)
        if (scope == ReportScope.FRIDGE && context.type() != Type.MANAGER) {
            throw new AccessDeniedException("냉장고 전체 리포트는 관리자만 볼 수 있어요.");
        }
        // 내 리포트: 내가 등록한 식품 기준 (공유 식품을 다른 멤버가 처리했어도 등록자의 기록)
        UUID memberId = scope == ReportScope.MY ? context.member().getId() : null;

        if (period == ReportPeriod.ALL) {
            return allReport(fridgeId, memberId, scope);
        }
        return monthReport(fridgeId, memberId, scope, parseMonth(month));
    }

    private FoodReportResDto monthReport(Long fridgeId, UUID memberId, ReportScope scope, YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime previousStart = month.minusMonths(1).atDay(1).atStartOfDay();

        // 지난달 비교를 위해 두 달 치를 한 번에 가져와 나눈다
        List<FoodHistory> twoMonths = foodHistoryRepository.findForReport(fridgeId, memberId, previousStart, end);
        List<FoodHistory> current = twoMonths.stream().filter(h -> !h.getEndedAt().isBefore(start)).toList();
        List<FoodHistory> previous = twoMonths.stream().filter(h -> h.getEndedAt().isBefore(start)).toList();

        Summary summary = summaryOf(current);
        int currentTotal = summary.getConsumedCount() + summary.getDiscardedCount();
        int previousTotal = sum(previous, FoodResult.소비) + sum(previous, FoodResult.폐기);
        if (currentTotal >= MIN_COUNT_FOR_COMPARE && previousTotal >= MIN_COUNT_FOR_COMPARE) {
            Double previousRate = rate(sum(previous, FoodResult.소비), sum(previous, FoodResult.폐기));
            summary.setDiscardRateDiff(round1(summary.getDiscardRate() - previousRate));
        }

        FoodReportResDto report = baseReport(ReportPeriod.MONTH, scope, current, summary);
        report.setFrom(month.atDay(1));
        report.setTo(month.atEndOfMonth());
        report.setMonthlyTrend(List.of());

        List<String> insights = new ArrayList<>();
        if (summary.getDiscardRateDiff() != null) {
            insights.add(diffSentence("지난달", summary.getDiscardRateDiff()));
        }
        insights.addAll(commonInsights(report));
        report.setInsights(insights);
        return report;
    }

    private FoodReportResDto allReport(Long fridgeId, UUID memberId, ReportScope scope) {
        LocalDate today = LocalDate.now();
        List<FoodHistory> histories = foodHistoryRepository.findForReport(
                fridgeId, memberId, BEGINNING, today.plusDays(1).atStartOfDay());

        FoodReportResDto report = baseReport(ReportPeriod.ALL, scope, histories, summaryOf(histories));
        report.setFrom(histories.stream()
                .map(h -> h.getEndedAt().toLocalDate())
                .min(Comparator.naturalOrder())
                .orElse(null));
        report.setTo(today);
        report.setMonthlyTrend(monthlyTrend(histories));

        List<String> insights = new ArrayList<>();
        trendSentence(report.getMonthlyTrend()).ifPresent(insights::add);
        insights.addAll(commonInsights(report));
        report.setInsights(insights);
        return report;
    }

    // 월간·전체에 공통인 집계
    private FoodReportResDto baseReport(ReportPeriod period, ReportScope scope, List<FoodHistory> histories, Summary summary) {
        return FoodReportResDto.builder()
                .period(period)
                .scope(scope)
                .summary(summary)
                .byCategory(byCategory(histories))
                .discardTiming(discardTiming(histories))
                .avgStorageDays(StorageDays.builder()
                        .consumed(avgStorageDays(histories, FoodResult.소비))
                        .discarded(avgStorageDays(histories, FoodResult.폐기))
                        .build())
                .topDiscarded(topDiscarded(histories))
                .build();
    }

    private Summary summaryOf(List<FoodHistory> histories) {
        int consumed = sum(histories, FoodResult.소비);
        int discarded = sum(histories, FoodResult.폐기);
        return Summary.builder()
                .consumedCount(consumed)
                .discardedCount(discarded)
                .discardRate(rate(consumed, discarded))
                .build();
    }

    private List<CategoryStat> byCategory(List<FoodHistory> histories) {
        Map<Category, List<FoodHistory>> grouped = histories.stream()
                .collect(Collectors.groupingBy(h -> h.getCategory() == null ? Category.기타 : h.getCategory()));
        return grouped.entrySet().stream()
                .map(e -> CategoryStat.builder()
                        .category(e.getKey())
                        .consumed(sum(e.getValue(), FoodResult.소비))
                        .discarded(sum(e.getValue(), FoodResult.폐기))
                        .build())
                .sorted(Comparator.comparingInt(CategoryStat::getDiscarded).reversed()
                        .thenComparing(Comparator.comparingInt((CategoryStat c) -> c.getConsumed() + c.getDiscarded()).reversed())
                        .thenComparing(CategoryStat::getCategory))
                .toList();
    }

    private DiscardTiming discardTiming(List<FoodHistory> histories) {
        int after = 0;
        int before = 0;
        for (FoodHistory h : histories) {
            if (h.getResult() != FoodResult.폐기) continue;
            if (h.getEndedAt().isAfter(h.getExpirationDateTime())) {
                after += h.getQuantity();
            } else {
                before += h.getQuantity();
            }
        }
        return DiscardTiming.builder().afterExpiration(after).beforeExpiration(before).build();
    }

    // 등록일부터 처리한 날까지의 평균 일수 (수량만큼 가중)
    private Double avgStorageDays(List<FoodHistory> histories, FoodResult result) {
        long days = 0;
        int count = 0;
        for (FoodHistory h : histories) {
            if (h.getResult() != result) continue;
            long stored = Math.max(0, ChronoUnit.DAYS.between(h.getStorageStartDate(), h.getEndedAt().toLocalDate()));
            days += stored * h.getQuantity();
            count += h.getQuantity();
        }
        return count == 0 ? null : round1((double) days / count);
    }

    private List<NameCount> topDiscarded(List<FoodHistory> histories) {
        Map<String, Integer> byName = histories.stream()
                .filter(h -> h.getResult() == FoodResult.폐기)
                .collect(Collectors.groupingBy(h -> h.getName().trim(), Collectors.summingInt(FoodHistory::getQuantity)));
        return byName.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_DISCARDED_SIZE)
                .map(e -> NameCount.builder().name(e.getKey()).count(e.getValue()).build())
                .toList();
    }

    private List<MonthStat> monthlyTrend(List<FoodHistory> histories) {
        Map<YearMonth, List<FoodHistory>> byMonth = histories.stream()
                .collect(Collectors.groupingBy(h -> YearMonth.from(h.getEndedAt()), TreeMap::new, Collectors.toList()));
        return byMonth.entrySet().stream()
                .map(e -> {
                    int consumed = sum(e.getValue(), FoodResult.소비);
                    int discarded = sum(e.getValue(), FoodResult.폐기);
                    return MonthStat.builder()
                            .month(e.getKey().toString())
                            .consumed(consumed)
                            .discarded(discarded)
                            .discardRate(rate(consumed, discarded))
                            .build();
                })
                .toList();
    }

    // ===== 인사이트 문장 (LLM 없이 규칙으로 만든다) =====

    private List<String> commonInsights(FoodReportResDto report) {
        List<String> insights = new ArrayList<>();
        Summary summary = report.getSummary();
        int discarded = summary.getDiscardedCount();

        if (summary.getConsumedCount() > 0 && discarded == 0) {
            insights.add("버린 식품 없이 모두 먹었어요");
            return insights;
        }
        if (discarded < MIN_DISCARDED_FOR_INSIGHT) {
            return insights;
        }

        CategoryStat topCategory = report.getByCategory().get(0); // 버린 개수가 많은 순으로 정렬되어 있음
        if (topCategory.getDiscarded() >= MIN_DISCARDED_FOR_INSIGHT) {
            String name = topCategory.getCategory().name();
            insights.add(name + objectParticle(name) + " 가장 많이 버렸어요 (" + topCategory.getDiscarded() + "개)");
        }

        int afterExpiration = report.getDiscardTiming().getAfterExpiration();
        if (afterExpiration > 0) {
            insights.add("버린 " + discarded + "개 중 " + afterExpiration + "개는 유통기한이 지난 뒤였어요");
        }

        StorageDays days = report.getAvgStorageDays();
        if (days.getConsumed() != null && days.getDiscarded() != null && days.getDiscarded() > days.getConsumed()) {
            insights.add("버린 식품은 평균 " + formatNumber(days.getDiscarded()) + "일, 먹은 식품은 "
                    + formatNumber(days.getConsumed()) + "일 보관했어요");
        }
        return insights;
    }

    // 전체 리포트: 기록이 충분한 첫 달과 마지막 달의 폐기율 비교
    private Optional<String> trendSentence(List<MonthStat> trend) {
        List<MonthStat> enough = trend.stream()
                .filter(m -> m.getConsumed() + m.getDiscarded() >= MIN_COUNT_FOR_COMPARE)
                .toList();
        if (enough.size() < 2) {
            return Optional.empty();
        }
        MonthStat first = enough.get(0);
        MonthStat last = enough.get(enough.size() - 1);
        int firstMonth = YearMonth.parse(first.getMonth()).getMonthValue();
        return Optional.of(diffSentence("처음(" + firstMonth + "월)", round1(last.getDiscardRate() - first.getDiscardRate())));
    }

    private String diffSentence(String base, double diff) {
        if (diff < 0) return base + "보다 폐기율이 " + formatNumber(-diff) + "%p 줄었어요";
        if (diff > 0) return base + "보다 폐기율이 " + formatNumber(diff) + "%p 늘었어요";
        return base + "과 폐기율이 같아요";
    }

    // ===== 계산 도우미 =====

    private YearMonth parseMonth(String month) {
        YearMonth now = YearMonth.now();
        if (!StringUtils.hasText(month)) {
            return now;
        }
        YearMonth parsed;
        try {
            parsed = YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("월은 2026-11 형식으로 보내 주세요.");
        }
        if (parsed.isAfter(now)) {
            throw new IllegalArgumentException("아직 오지 않은 달의 리포트는 볼 수 없어요.");
        }
        return parsed;
    }

    private static int sum(List<FoodHistory> histories, FoodResult result) {
        return histories.stream().filter(h -> h.getResult() == result).mapToInt(FoodHistory::getQuantity).sum();
    }

    private static Double rate(int consumed, int discarded) {
        int total = consumed + discarded;
        return total == 0 ? null : round1(discarded * 100.0 / total);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    // 3.0 → "3", 3.5 → "3.5"
    private static String formatNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // 받침이 있으면 "을", 없으면 "를" (유제품을, 채소를)
    private static String objectParticle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < '가' || last > '힣') return "를";
        return (last - '가') % 28 == 0 ? "를" : "을";
    }
}
