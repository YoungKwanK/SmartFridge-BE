package com.be16_2nd.SmartFridge.receipt.service;

import com.be16_2nd.SmartFridge.common.ai.AiClient;
import com.be16_2nd.SmartFridge.common.ai.AiEffort;
import com.be16_2nd.SmartFridge.common.ai.AiImage;
import com.be16_2nd.SmartFridge.common.ai.AiPrompt;
import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.receipt.dto.ReceiptAiResult;
import com.be16_2nd.SmartFridge.receipt.dto.ReceiptFoodDraftDto;
import com.be16_2nd.SmartFridge.receipt.dto.ReceiptRecognizeResDto;
import com.be16_2nd.SmartFridge.receipt.dto.ReceiptSourceType;
import com.be16_2nd.SmartFridge.receipt.exception.ReceiptException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 영수증·주문내역 사진을 AI로 읽어 식품 등록 칸에 채울 초안을 만든다. 아무것도 저장하지 않는다.
 * 사용자가 확인·수정한 초안은 기존 식품 등록 API(POST /fridge/{fridgeId}/food/register)로 등록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptRecognizeService {

    static final long MAX_TOKENS = 8000L;
    static final int MIN_SHELF_LIFE_DAYS = 1;
    static final int MAX_SHELF_LIFE_DAYS = 730;
    static final int MAX_QUANTITY = 999;

    private final AiClient aiClient;
    private final FridgeAccessValidator fridgeAccessValidator;
    private final ReceiptUsageStore usageStore;

    public ReceiptRecognizeResDto recognize(Long fridgeId, MultipartFile image) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        AiImage aiImage = ReceiptImageValidator.validate(image);

        UUID memberId = context.member().getId();
        if (usageStore.increaseUsage(memberId) > ReceiptUsageStore.DAILY_LIMIT) {
            throw ReceiptException.dailyLimitExceeded(ReceiptUsageStore.DAILY_LIMIT);
        }

        LocalDate today = LocalDate.now();
        ReceiptAiResult result = aiClient.generate(AiPrompt.builder()
                .system(ReceiptPrompts.SYSTEM)
                .user(ReceiptPrompts.user(today))
                .images(List.of(aiImage))
                .effort(AiEffort.LOW)
                .maxTokens(MAX_TOKENS)
                .build(), ReceiptAiResult.class);

        ReceiptRecognizeResDto response = toResponse(result, today);
        log.info("영수증 인식 fridgeId={} source={} foods={}", fridgeId, response.sourceType(), response.foods().size());
        return response;
    }

    /** AI 결과를 등록 칸 초안으로 다듬는다. 날짜·수량·보관 일수는 AI 값을 그대로 믿지 않고 범위를 맞춘다 */
    static ReceiptRecognizeResDto toResponse(ReceiptAiResult result, LocalDate today) {
        List<ReceiptAiResult.Item> items = result.items() == null ? List.of() : result.items();
        List<ReceiptFoodDraftDto> foods = items.stream()
                .filter(item -> hasText(item.name()) || hasText(item.rawName()))
                .map(item -> toDraft(item, today))
                .toList();
        ReceiptSourceType sourceType = result.sourceType() == null ? ReceiptSourceType.UNKNOWN : result.sourceType();
        String note = result.note().filter(ReceiptRecognizeService::hasText).orElse(null);
        return new ReceiptRecognizeResDto(sourceType, foods, note);
    }

    static ReceiptFoodDraftDto toDraft(ReceiptAiResult.Item item, LocalDate today) {
        int orderQuantity = clamp(item.quantity(), 1, MAX_QUANTITY);
        Integer packCount = item.packCount().filter(count -> count > 1).orElse(null);
        int quantity = packCount == null ? orderQuantity : clamp(orderQuantity * packCount, 1, MAX_QUANTITY);

        LocalDate purchaseDate = parsePurchaseDate(item.purchaseDate(), today);
        int shelfLifeDays = clamp(item.shelfLifeDays(), MIN_SHELF_LIFE_DAYS, MAX_SHELF_LIFE_DAYS);
        LocalDate expirationDate = (purchaseDate != null ? purchaseDate : today).plusDays(shelfLifeDays);

        return ReceiptFoodDraftDto.builder()
                .name(hasText(item.name()) ? item.name().trim() : item.rawName().trim())
                .quantity(quantity)
                .storageType(item.storage())
                .category(item.category())
                .expirationDate(expirationDate)
                .shareable(false)
                .rawName(item.rawName())
                .orderQuantity(orderQuantity)
                .packCount(packCount)
                .amount(item.amount().filter(ReceiptRecognizeService::hasText).orElse(null))
                .purchaseDate(purchaseDate)
                .shelfLifeDays(shelfLifeDays)
                .build();
    }

    // 형식이 틀리거나 미래 날짜면 모르는 날짜로 본다
    static LocalDate parsePurchaseDate(Optional<String> value, LocalDate today) {
        if (value.isEmpty() || !hasText(value.get())) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.get().trim());
            return date.isAfter(today) ? null : date;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
