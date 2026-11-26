package com.be16_2nd.SmartFridge.food.service;

import com.be16_2nd.SmartFridge.common.dto.CommonErrorDto;
import com.be16_2nd.SmartFridge.common.service.CommonExceptionHandler;
import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import com.be16_2nd.SmartFridge.food.dto.FoodCreateDto;
import com.be16_2nd.SmartFridge.food.dto.FoodUpdateDto;
import com.be16_2nd.SmartFridge.food.repository.FoodHistoryRepository;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.member.domain.Member;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.TransactionSystemException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * QA에서 나온 식품 수정 오류 두 가지 단위 테스트.
 * - B2: 수정할 때 시각을 비우면 500(NPE)이던 문제 → 등록과 같이 23:59로 채운다
 * - B3: 동시 수정 시 MariaDB 오류 1020이 500으로 나가던 문제 → @Version 충돌과 같은 409로 안내한다
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인하므로 로컬 DB에 영향이 없다.
 */
@ExtendWith(MockitoExtension.class)
class FoodUpdateErrorTest {

    @Mock FoodRepository foodRepository;
    @Mock FoodHistoryRepository foodHistoryRepository;
    @Mock FridgeAccessValidator fridgeAccessValidator;

    @InjectMocks FoodService foodService;

    final Long fridgeId = 1L;
    final Long foodId = 10L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member me = Member.builder().id(UUID.randomUUID()).name("홍길동").build();
    final LocalDate date = LocalDate.of(2026, 12, 1);

    // ---- B2: 유통기한 시각 ----

    @Test
    void 수정할_때_시각을_비우면_그날_23시_59분까지로_저장한다() {
        Food cucumber = givenMyFood();

        foodService.updateFood(fridgeId, foodId, updateDto(date, null, null));

        assertThat(cucumber.getExpirationDateTime()).isEqualTo(date.atTime(23, 59));
    }

    @Test
    void 수정할_때_시각을_넣으면_그_시각으로_저장한다() {
        Food cucumber = givenMyFood();

        foodService.updateFood(fridgeId, foodId, updateDto(date, 9, 30));

        assertThat(cucumber.getExpirationDateTime()).isEqualTo(date.atTime(9, 30));
    }

    @Test
    void 수정할_때_날짜를_비우면_400으로_안내한다() {
        givenMyFood();

        assertThatThrownBy(() -> foodService.updateFood(fridgeId, foodId, updateDto(null, 9, 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유통기한 날짜는 필수입니다.");
    }

    @Test
    void 범위를_벗어난_시각은_400으로_안내한다() {
        assertThatThrownBy(() -> Food.expirationOf(date, 24, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("23시 59분");
        assertThatThrownBy(() -> Food.expirationOf(date, 10, 60))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Food.expirationOf(date, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 등록도_수정과_같은_규칙으로_시각을_채운다() {
        FoodCreateDto dto = FoodCreateDto.builder()
                .name("오이").quantity(1).storageType(StorageType.냉장).category(Category.채소)
                .expirationDate(date).shareable(false)
                .build();

        assertThat(dto.toEntity(me, fridge).getExpirationDateTime()).isEqualTo(date.atTime(23, 59));
    }

    // ---- B3: 동시 수정 충돌 ----

    final CommonExceptionHandler handler = new CommonExceptionHandler();

    @Test
    void 커밋할_때_MariaDB_1020_충돌이_나면_409로_안내한다() {
        // 스프링이 감싸는 모양: JpaSystemException ← PersistenceException ← SQLException(1020)
        Exception wrapped = new JpaSystemException(new PersistenceException("could not execute statement",
                recordChanged()));

        assertConflict(handler.dataAccessException(wrapped));
    }

    @Test
    void 트랜잭션_예외로_감싸져_와도_1020이면_409로_안내한다() {
        Exception wrapped = new TransactionSystemException("커밋 실패",
                new PersistenceException(recordChanged()));

        assertConflict(handler.dataAccessException(wrapped));
    }

    @Test
    void 다른_DB_오류는_예전처럼_500으로_둔다() {
        Exception other = new DataIntegrityViolationException("중복",
                new SQLException("Duplicate entry", "23000", 1062));

        ResponseEntity<?> response = handler.dataAccessException(other);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static SQLException recordChanged() {
        return new SQLException("Record has changed since last read in table 'food'", "HY000", 1020);
    }

    private static void assertConflict(ResponseEntity<?> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((CommonErrorDto) response.getBody()).getStatus_message()).contains("다른 멤버가 먼저");
    }

    private Food givenMyFood() {
        Food food = Food.builder()
                .id(foodId)
                .name("오이")
                .quantity(1)
                .category(Category.채소)
                .storageType(StorageType.냉장)
                .storageStartDate(LocalDate.now())
                .expirationDateTime(LocalDateTime.now().plusDays(2))
                .isShared(false)
                .member(me)
                .fridge(fridge)
                .build();
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, Type.COMMON));
        when(foodRepository.findById(foodId)).thenReturn(Optional.of(food));
        return food;
    }

    private FoodUpdateDto updateDto(LocalDate expirationDate, Integer hour, Integer minute) {
        return FoodUpdateDto.builder()
                .name("오이")
                .quantity(1)
                .storageType(StorageType.냉장)
                .category(Category.채소)
                .expirationDate(expirationDate)
                .expirationHour(hour)
                .expirationMinute(minute)
                .shareable(false)
                .build();
    }
}
