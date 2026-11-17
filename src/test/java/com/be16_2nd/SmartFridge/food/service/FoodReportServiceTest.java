package com.be16_2nd.SmartFridge.food.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.domain.FoodHistory;
import com.be16_2nd.SmartFridge.food.domain.FoodResult;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import com.be16_2nd.SmartFridge.food.dto.FoodReportResDto;
import com.be16_2nd.SmartFridge.food.dto.ReportPeriod;
import com.be16_2nd.SmartFridge.food.dto.ReportScope;
import com.be16_2nd.SmartFridge.food.repository.FoodHistoryRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 식품 리포트(월간·전체) 집계와 인사이트 문장 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인하므로 로컬 DB에 영향이 없다.
 */
@ExtendWith(MockitoExtension.class)
class FoodReportServiceTest {

    @Mock FoodHistoryRepository foodHistoryRepository;
    @Mock FridgeAccessValidator fridgeAccessValidator;

    @InjectMocks FoodReportService foodReportService;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member me = Member.builder().id(UUID.randomUUID()).name("홍길동").build();

    final YearMonth thisMonth = YearMonth.now();
    final YearMonth lastMonth = thisMonth.minusMonths(1);

    // 기본은 일반 멤버로 로그인. 냉장고 전체 리포트 테스트는 loginAs(Type.MANAGER)로 바꾼다
    @BeforeEach
    void login() {
        lenient().when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, Type.COMMON));
    }

    private void loginAs(Type type) {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, type));
    }

    @Test
    void 월간_리포트는_이번_달_기록을_개수로_합치고_지난달과_비교한다() {
        List<FoodHistory> histories = new ArrayList<>();
        // 이번 달: 소비 6, 폐기 4 (유제품 3, 채소 1) → 폐기율 40%
        histories.add(history("우유", Category.유제품, FoodResult.소비, 6, day(thisMonth, 1), 3, 2));
        histories.add(history("요구르트", Category.유제품, FoodResult.폐기, 3, day(thisMonth, 1), 10, 5));  // 유통기한 지난 뒤 버림
        histories.add(history("시금치", Category.채소, FoodResult.폐기, 1, day(thisMonth, 1), 2, 4));      // 유통기한 전에 버림
        // 지난달: 소비 8, 폐기 2 → 폐기율 20%
        histories.add(history("우유", Category.유제품, FoodResult.소비, 8, day(lastMonth, 1), 2, 5));
        histories.add(history("두부", Category.가공식품, FoodResult.폐기, 2, day(lastMonth, 1), 9, 3));
        givenHistories(histories);

        FoodReportResDto report = foodReportService.getReport(fridgeId, ReportPeriod.MONTH, ReportScope.MY, thisMonth.toString());

        assertThat(report.getFrom()).isEqualTo(thisMonth.atDay(1));
        assertThat(report.getTo()).isEqualTo(thisMonth.atEndOfMonth());
        assertThat(report.getSummary().getConsumedCount()).isEqualTo(6);
        assertThat(report.getSummary().getDiscardedCount()).isEqualTo(4);
        assertThat(report.getSummary().getDiscardRate()).isEqualTo(40.0);
        assertThat(report.getSummary().getDiscardRateDiff()).isEqualTo(20.0);

        assertThat(report.getByCategory().get(0).getCategory()).isEqualTo(Category.유제품);
        assertThat(report.getByCategory().get(0).getDiscarded()).isEqualTo(3);
        assertThat(report.getDiscardTiming().getAfterExpiration()).isEqualTo(3);
        assertThat(report.getDiscardTiming().getBeforeExpiration()).isEqualTo(1);
        assertThat(report.getTopDiscarded()).extracting(FoodReportResDto.NameCount::getName)
                .containsExactly("요구르트", "시금치");
        assertThat(report.getMonthlyTrend()).isEmpty();

        assertThat(report.getInsights()).containsExactly(
                "지난달보다 폐기율이 20%p 늘었어요",
                "유제품을 가장 많이 버렸어요 (3개)",
                "버린 4개 중 3개는 유통기한이 지난 뒤였어요",
                "버린 식품은 평균 8일, 먹은 식품은 3일 보관했어요"); // (10일×3개 + 2일×1개) / 4개

    }

    @Test
    void 지난달_기록이_적으면_비교하지_않는다() {
        givenHistories(List.of(
                history("상추", Category.채소, FoodResult.폐기, 3, day(thisMonth, 1), 1, 5),
                history("우유", Category.유제품, FoodResult.소비, 3, day(thisMonth, 1), 1, 5),
                history("두부", Category.가공식품, FoodResult.폐기, 1, day(lastMonth, 1), 1, 5)));

        FoodReportResDto report = foodReportService.getReport(fridgeId, ReportPeriod.MONTH, ReportScope.MY, null);

        assertThat(report.getSummary().getDiscardRateDiff()).isNull();
        assertThat(report.getInsights()).noneMatch(s -> s.contains("지난달"));
        assertThat(report.getInsights()).contains("채소를 가장 많이 버렸어요 (3개)");
    }

    @Test
    void 내_리포트는_내가_등록한_식품만_냉장고_전체는_모두_조회한다() {
        givenHistories(List.of());

        foodReportService.getReport(fridgeId, ReportPeriod.MONTH, ReportScope.MY, null);
        verify(foodHistoryRepository).findForReport(eq(fridgeId), eq(me.getId()), any(), any());

        loginAs(Type.MANAGER);
        foodReportService.getReport(fridgeId, ReportPeriod.MONTH, ReportScope.FRIDGE, null);
        verify(foodHistoryRepository).findForReport(eq(fridgeId), isNull(), any(), any());
    }

    @Test
    void 일반_멤버는_냉장고_전체_리포트를_볼_수_없다() {
        assertThatThrownBy(() -> foodReportService.getReport(fridgeId, ReportPeriod.MONTH, ReportScope.FRIDGE, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("관리자만");
        assertThatThrownBy(() -> foodReportService.getReport(fridgeId, ReportPeriod.ALL, ReportScope.FRIDGE, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(foodHistoryRepository);
    }

    @Test
    void 기록이_없으면_0과_빈_목록을_돌려준다() {
        givenHistories(List.of());

        FoodReportResDto report = foodReportService.getReport(fridgeId, ReportPeriod.ALL, ReportScope.MY, null);

        assertThat(report.getFrom()).isNull();
        assertThat(report.getSummary().getConsumedCount()).isZero();
        assertThat(report.getSummary().getDiscardRate()).isNull();
        assertThat(report.getAvgStorageDays().getConsumed()).isNull();
        assertThat(report.getByCategory()).isEmpty();
        assertThat(report.getTopDiscarded()).isEmpty();
        assertThat(report.getMonthlyTrend()).isEmpty();
        assertThat(report.getInsights()).isEmpty();
    }

    @Test
    void 버린_식품이_없으면_칭찬_문장을_보여준다() {
        givenHistories(List.of(history("우유", Category.유제품, FoodResult.소비, 2, day(thisMonth, 1), 3, 1)));

        FoodReportResDto report = foodReportService.getReport(fridgeId, ReportPeriod.MONTH, ReportScope.MY, null);

        assertThat(report.getSummary().getDiscardRate()).isEqualTo(0.0);
        assertThat(report.getInsights()).containsExactly("버린 식품 없이 모두 먹었어요");
    }

    @Test
    void 전체_리포트는_첫_기록부터_월별_추이와_처음_대비_변화를_보여준다() {
        YearMonth twoMonthsAgo = thisMonth.minusMonths(2);
        givenHistories(List.of(
                // 두 달 전: 소비 6, 폐기 4 → 40%
                history("우유", Category.유제품, FoodResult.소비, 6, day(twoMonthsAgo, 3), 3, 1),
                history("상추", Category.채소, FoodResult.폐기, 4, day(twoMonthsAgo, 5), 1, 6),
                // 지난달: 기록 1개 (비교에서 빠짐)
                history("두부", Category.가공식품, FoodResult.소비, 1, day(lastMonth, 1), 3, 1),
                // 이번 달: 소비 9, 폐기 1 → 10%
                history("우유", Category.유제품, FoodResult.소비, 9, day(thisMonth, 1), 3, 1),
                history("상추", Category.채소, FoodResult.폐기, 1, day(thisMonth, 1), 1, 3)));

        loginAs(Type.MANAGER);
        FoodReportResDto report = foodReportService.getReport(fridgeId, ReportPeriod.ALL, ReportScope.FRIDGE, null);

        assertThat(report.getFrom()).isEqualTo(day(twoMonthsAgo, 3).toLocalDate());
        assertThat(report.getTo()).isEqualTo(LocalDate.now());
        assertThat(report.getMonthlyTrend()).extracting(FoodReportResDto.MonthStat::getMonth)
                .containsExactly(twoMonthsAgo.toString(), lastMonth.toString(), thisMonth.toString());
        assertThat(report.getMonthlyTrend().get(0).getDiscardRate()).isEqualTo(40.0);
        assertThat(report.getSummary().getDiscardRateDiff()).isNull();
        assertThat(report.getInsights().get(0))
                .isEqualTo("처음(" + twoMonthsAgo.getMonthValue() + "월)보다 폐기율이 30%p 줄었어요");
    }

    @Test
    void 아직_오지_않은_달이나_형식이_틀린_달은_볼_수_없다() {
        assertThatThrownBy(() -> foodReportService.getReport(fridgeId, ReportPeriod.MONTH, ReportScope.MY,
                thisMonth.plusMonths(1).toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("아직 오지 않은 달");
        assertThatThrownBy(() -> foodReportService.getReport(fridgeId, ReportPeriod.MONTH, ReportScope.MY, "11월"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-11 형식");

        verifyNoInteractions(foodHistoryRepository);
    }

    private void givenHistories(List<FoodHistory> histories) {
        when(foodHistoryRepository.findForReport(eq(fridgeId), any(), any(), any())).thenReturn(histories);
    }

    // 그 달 n일 정오
    private LocalDateTime day(YearMonth month, int dayOfMonth) {
        return month.atDay(dayOfMonth).atTime(12, 0);
    }

    /**
     * @param storedDays     처리한 날 기준 며칠 전에 등록했는지
     * @param expiresInDays  등록일 기준 유통기한까지의 일수 (보관일보다 짧으면 유통기한이 지난 뒤 처리한 것)
     */
    private FoodHistory history(String name, Category category, FoodResult result, int quantity,
                                LocalDateTime endedAt, int storedDays, int expiresInDays) {
        LocalDate startDate = endedAt.toLocalDate().minusDays(storedDays);
        Food food = Food.builder()
                .name(name)
                .quantity(quantity)
                .category(category)
                .storageType(StorageType.냉장)
                .storageStartDate(startDate)
                .expirationDateTime(startDate.plusDays(expiresInDays).atTime(23, 59))
                .isShared(true)
                .member(me)
                .fridge(fridge)
                .build();
        return FoodHistory.of(food, result, quantity, endedAt);
    }
}
