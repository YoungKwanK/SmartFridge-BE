package com.be16_2nd.SmartFridge.receipt.service;

import com.be16_2nd.SmartFridge.common.ai.AiClient;
import com.be16_2nd.SmartFridge.common.ai.AiEffort;
import com.be16_2nd.SmartFridge.common.ai.AiPrompt;
import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.receipt.dto.ReceiptAiResult;
import com.be16_2nd.SmartFridge.receipt.dto.ReceiptFoodDraftDto;
import com.be16_2nd.SmartFridge.receipt.dto.ReceiptRecognizeResDto;
import com.be16_2nd.SmartFridge.receipt.dto.ReceiptSourceType;
import com.be16_2nd.SmartFridge.receipt.exception.ReceiptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReceiptRecognizeServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    final Long fridgeId = 1L;
    final Member me = Member.builder().id(UUID.randomUUID()).name("홍길동").build();
    final MockMultipartFile jpeg = new MockMultipartFile("image", "receipt.jpg", "image/jpeg",
            ReceiptImageValidatorTest.JPEG);

    AiClient aiClient;
    FridgeAccessValidator fridgeAccessValidator;
    ReceiptUsageStore usageStore;
    ReceiptRecognizeService service;

    @BeforeEach
    void setUp() {
        aiClient = mock(AiClient.class);
        fridgeAccessValidator = mock(FridgeAccessValidator.class);
        usageStore = mock(ReceiptUsageStore.class);
        service = new ReceiptRecognizeService(aiClient, fridgeAccessValidator, usageStore);

        Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, me, Type.COMMON));
        when(usageStore.increaseUsage(me.getId())).thenReturn(1L);
    }

    // ===== 요청 흐름 =====

    @Test
    void 사진을_이미지로_담아_low_effort로_요청하고_초안을_돌려준다() {
        when(aiClient.generate(any(AiPrompt.class), eq(ReceiptAiResult.class)))
                .thenReturn(result(item("*서울우유 1L", "우유", 2, null, "2026-11-18", 10)));

        ReceiptRecognizeResDto response = service.recognize(fridgeId, jpeg);

        ArgumentCaptor<AiPrompt> captor = ArgumentCaptor.forClass(AiPrompt.class);
        verify(aiClient).generate(captor.capture(), eq(ReceiptAiResult.class));
        AiPrompt prompt = captor.getValue();
        assertThat(prompt.images()).hasSize(1);
        assertThat(prompt.images().get(0).mediaType()).isEqualTo("image/jpeg");
        assertThat(prompt.effort()).isEqualTo(AiEffort.LOW);
        assertThat(prompt.user()).contains("오늘 날짜: " + LocalDate.now());

        assertThat(response.sourceType()).isEqualTo(ReceiptSourceType.RECEIPT);
        assertThat(response.foods()).extracting(ReceiptFoodDraftDto::getName).containsExactly("우유");
    }

    @Test
    void 냉장고_멤버가_아니면_사용량을_세지_않고_막는다() {
        when(fridgeAccessValidator.validate(fridgeId)).thenThrow(new AccessDeniedException("권한 없음"));

        assertThatThrownBy(() -> service.recognize(fridgeId, jpeg)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(usageStore, aiClient);
    }

    @Test
    void 잘못된_사진은_사용량을_세지_않고_막는다() {
        MockMultipartFile html = new MockMultipartFile("image", "a.jpg", "image/jpeg", "<html>".getBytes());

        assertThatThrownBy(() -> service.recognize(fridgeId, html)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(usageStore, aiClient);
    }

    @Test
    void 하루_사용_횟수를_넘으면_AI를_부르지_않고_429() {
        when(usageStore.increaseUsage(me.getId())).thenReturn(ReceiptUsageStore.DAILY_LIMIT + 1L);

        assertThatThrownBy(() -> service.recognize(fridgeId, jpeg))
                .isInstanceOf(ReceiptException.class)
                .extracting("status").isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(aiClient, never()).generate(any(AiPrompt.class), eq(ReceiptAiResult.class));
    }

    // ===== AI 결과 다듬기 =====

    @Test
    void 유통기한은_구매일에_보관_일수를_더해_추천한다() {
        ReceiptFoodDraftDto draft = ReceiptRecognizeService.toDraft(
                item("*한돈생삼겹살", "삼겹살", 1, null, "2026-10-28", 3), TODAY);

        assertThat(draft.getPurchaseDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(draft.getExpirationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(draft.getShelfLifeDays()).isEqualTo(3);
        assertThat(draft.getCategory()).isEqualTo(Category.고기);
        assertThat(draft.getStorageType()).isEqualTo(StorageType.냉장);
        assertThat(draft.getShareable()).isFalse();
    }

    @Test
    void 구매일을_모르면_오늘을_기준으로_추천하고_구매일은_비워_둔다() {
        ReceiptFoodDraftDto draft = ReceiptRecognizeService.toDraft(item("깐마늘", "깐마늘", 1, null, null, 7), TODAY);

        assertThat(draft.getPurchaseDate()).isNull();
        assertThat(draft.getExpirationDate()).isEqualTo(TODAY.plusDays(7));
    }

    @Test
    void 형식이_틀리거나_미래인_구매일은_모르는_날짜로_본다() {
        assertThat(ReceiptRecognizeService.parsePurchaseDate(Optional.of("26년 9월"), TODAY)).isNull();
        assertThat(ReceiptRecognizeService.parsePurchaseDate(Optional.of("2026-11-20"), TODAY)).isNull();
        assertThat(ReceiptRecognizeService.parsePurchaseDate(Optional.of(" "), TODAY)).isNull();
        assertThat(ReceiptRecognizeService.parsePurchaseDate(Optional.of("2026-11-19"), TODAY)).isEqualTo(TODAY);
    }

    @Test
    void 묶음_상품은_주문_수량에_묶음_개수를_곱해_등록_수량으로_쓴다() {
        ReceiptFoodDraftDto draft = ReceiptRecognizeService.toDraft(
                item("농심 큰사발 16개입", "컵라면", 2, 16, "2026-11-14", 180), TODAY);

        assertThat(draft.getQuantity()).isEqualTo(32);
        assertThat(draft.getOrderQuantity()).isEqualTo(2);
        assertThat(draft.getPackCount()).isEqualTo(16);
    }

    @Test
    void 수량과_보관_일수는_허용_범위로_맞춘다() {
        ReceiptFoodDraftDto zero = ReceiptRecognizeService.toDraft(item("우유", "우유", 0, null, null, 0), TODAY);
        ReceiptFoodDraftDto huge = ReceiptRecognizeService.toDraft(item("참치캔", "참치캔", 5000, null, null, 99999), TODAY);

        assertThat(zero.getQuantity()).isEqualTo(1);
        assertThat(zero.getShelfLifeDays()).isEqualTo(1);
        assertThat(huge.getQuantity()).isEqualTo(ReceiptRecognizeService.MAX_QUANTITY);
        assertThat(huge.getShelfLifeDays()).isEqualTo(ReceiptRecognizeService.MAX_SHELF_LIFE_DAYS);
    }

    @Test
    void 식품명이_비면_사진의_상품명을_쓰고_둘_다_없으면_뺀다() {
        ReceiptRecognizeResDto response = ReceiptRecognizeService.toResponse(result(
                item(" 풀)유부초밥280g ", " ", 1, null, null, 1),
                item(" ", null, 1, null, null, 1)), TODAY);

        assertThat(response.foods()).extracting(ReceiptFoodDraftDto::getName).containsExactly("풀)유부초밥280g");
    }

    @Test
    void 영수증이_아니면_빈_목록과_이유를_돌려준다() {
        ReceiptAiResult notReceipt = new ReceiptAiResult(null, null, Optional.of("영수증이 아닌 사진이에요."));

        ReceiptRecognizeResDto response = ReceiptRecognizeService.toResponse(notReceipt, TODAY);

        assertThat(response.sourceType()).isEqualTo(ReceiptSourceType.UNKNOWN);
        assertThat(response.foods()).isEmpty();
        assertThat(response.note()).isEqualTo("영수증이 아닌 사진이에요.");
    }

    private static ReceiptAiResult result(ReceiptAiResult.Item... items) {
        return new ReceiptAiResult(ReceiptSourceType.RECEIPT, List.of(items), Optional.empty());
    }

    private static ReceiptAiResult.Item item(String rawName, String name, int quantity, Integer packCount,
                                             String purchaseDate, int shelfLifeDays) {
        return new ReceiptAiResult.Item(rawName, name, quantity, Optional.ofNullable(packCount), Optional.empty(),
                Category.고기, StorageType.냉장, Optional.ofNullable(purchaseDate), shelfLifeDays);
    }
}
