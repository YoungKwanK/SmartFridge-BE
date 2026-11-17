package com.be16_2nd.SmartFridge.food.controller;

import com.be16_2nd.SmartFridge.common.dto.CommonDto;
import com.be16_2nd.SmartFridge.food.dto.FoodReportResDto;
import com.be16_2nd.SmartFridge.food.dto.ReportPeriod;
import com.be16_2nd.SmartFridge.food.dto.ReportScope;
import com.be16_2nd.SmartFridge.food.service.FoodReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/fridge/{fridgeId}/report")
public class FoodReportController {

    private final FoodReportService foodReportService;

    // 예: /fridge/1/report?period=MONTH&month=2026-11&scope=MY, /fridge/1/report?period=ALL&scope=FRIDGE
    @GetMapping
    public ResponseEntity<?> report(@PathVariable("fridgeId") Long fridgeId,
                                    @RequestParam(defaultValue = "MONTH") ReportPeriod period,
                                    @RequestParam(defaultValue = "MY") ReportScope scope,
                                    @RequestParam(required = false) String month) {
        FoodReportResDto report = foodReportService.getReport(fridgeId, period, scope, month);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(report)
                        .status_code(HttpStatus.OK.value())
                        .status_message("식품 리포트 조회 완료")
                        .build(),
                HttpStatus.OK);
    }
}
