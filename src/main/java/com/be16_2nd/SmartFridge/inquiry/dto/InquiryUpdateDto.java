package com.be16_2nd.SmartFridge.inquiry.dto;

import com.be16_2nd.SmartFridge.inquiry.domain.InquiryType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InquiryUpdateDto {
    private static final String NOT_BLANK_IF_PRESENT = "(?s).*\\S.*";

    private InquiryType inquiryType;

    @Pattern(regexp = NOT_BLANK_IF_PRESENT, message = "제목을 입력해 주세요")
    @Size(max = 255, message = "제목은 255자 이하로 입력해 주세요")
    private String title;

    @Pattern(regexp = NOT_BLANK_IF_PRESENT, message = "내용을 입력해 주세요")
    @Size(max = 5000, message = "내용은 5000자 이하로 입력해 주세요")
    private String contents;

    private List<MultipartFile> imageFiles = new ArrayList<>();

    // 프론트가 multipart 필드에 JSON 배열 문자열로 보낸다. 예: "[0,2]"
    private String removedImageIndexes;

    public List<Integer> parseRemovedImageIndexes() {
        if (removedImageIndexes == null || removedImageIndexes.isBlank()) {
            return List.of();
        }
        String value = removedImageIndexes.trim();
        if (!value.matches("\\[\\s*(\\d{1,4}\\s*(,\\s*\\d{1,4}\\s*)*)?]")) {
            throw new IllegalArgumentException("삭제할 이미지 정보가 올바르지 않습니다.");
        }
        String numbers = value.substring(1, value.length() - 1).trim();
        if (numbers.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(numbers.split(","))
                .map(number -> Integer.valueOf(number.trim()))
                .toList();
    }
}
