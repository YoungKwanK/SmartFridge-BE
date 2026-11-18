package com.be16_2nd.SmartFridge.receipt.controller;

import com.be16_2nd.SmartFridge.common.dto.CommonDto;
import com.be16_2nd.SmartFridge.receipt.dto.ReceiptRecognizeResDto;
import com.be16_2nd.SmartFridge.receipt.service.ReceiptRecognizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 영수증·주문내역 사진으로 식품 등록 칸 채우기. 인식 결과는 저장하지 않고 초안만 내려준다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/fridge/{fridgeId}/food/receipt")
public class ReceiptController {

    private final ReceiptRecognizeService receiptRecognizeService;

    // multipart 필드 이름: image (jpg, png, webp / 5MB 이하)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> recognize(@PathVariable("fridgeId") Long fridgeId,
                                       @RequestParam(value = "image", required = false) MultipartFile image) {
        ReceiptRecognizeResDto result = receiptRecognizeService.recognize(fridgeId, image);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(result)
                        .status_code(HttpStatus.OK.value())
                        .status_message("영수증 인식 완료")
                        .build(),
                HttpStatus.OK);
    }
}
