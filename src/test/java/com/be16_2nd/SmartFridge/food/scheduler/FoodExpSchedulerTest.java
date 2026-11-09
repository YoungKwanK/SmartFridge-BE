package com.be16_2nd.SmartFridge.food.scheduler;

import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.notification.domain.NotificationType;
import com.be16_2nd.SmartFridge.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 유통기한 알림 스케줄러 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class FoodExpSchedulerTest {

    @Mock FoodRepository foodRepository;
    @Mock NotificationService notificationService;

    @InjectMocks FoodExpScheduler foodExpScheduler;

    final Member member = Member.builder().name("홍길동").email("user@test.com").build();

    @Test
    @SuppressWarnings("unchecked")
    void 당일과_하루_전_식품만_조회한다() {
        LocalDate today = LocalDate.now();
        ArgumentCaptor<List<LocalDate>> dates = ArgumentCaptor.forClass(List.class);
        when(foodRepository.findAllByExpirationDateIn(dates.capture())).thenReturn(List.of());

        foodExpScheduler.notificationForExpFood();

        assertThat(dates.getValue()).containsExactly(today, today.plusDays(1));
    }

    @Test
    void 남은_기간에_맞는_문구로_알림을_보낸다() {
        LocalDate today = LocalDate.now();
        Food todayFood = food(today);
        Food tomorrowFood = food(today.plusDays(1));
        when(foodRepository.findAllByExpirationDateIn(List.of(today, today.plusDays(1))))
                .thenReturn(List.of(todayFood, tomorrowFood));

        foodExpScheduler.notificationForExpFood();

        verify(notificationService).createAndSendForExpiration(member, todayFood, "오늘까지입니다.");
        verify(notificationService).createAndSendForExpiration(member, tomorrowFood, "1일 남았습니다.");
    }

    @Test
    void 식품_등록_알림_타입은_더_이상_없다() {
        assertThat(Arrays.stream(NotificationType.values()).map(Enum::name)).doesNotContain("NEW_FOOD");
    }

    private Food food(LocalDate expirationDate) {
        return Food.builder()
                .name("우유")
                .member(member)
                .expirationDateTime(expirationDate.atTime(23, 59))
                .build();
    }
}
