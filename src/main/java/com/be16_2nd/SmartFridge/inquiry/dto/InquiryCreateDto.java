package com.be16_2nd.SmartFridge.inquiry.dto;

import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.inquiry.domain.Inquiry;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryType;
import com.be16_2nd.SmartFridge.member.domain.Member;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InquiryCreateDto {
    @NotNull(message = "문의 유형을 선택해 주세요")
    private InquiryType inquiryType;

    @NotBlank(message = "제목을 입력해 주세요")
    @Size(max = 255, message = "제목은 255자 이하로 입력해 주세요")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요")
    @Size(max = 5000, message = "내용은 5000자 이하로 입력해 주세요")
    private String contents;

    private List<MultipartFile> imageFiles = new ArrayList<>();

    // 문의를 등록하는 화면의 현재 냉장고 (선택 값)
    private Long fridgeId;

    public Inquiry toEntity(Member member, Fridge fridge) {
        return Inquiry.builder()
                .inquiryType(inquiryType)
                .title(title)
                .contents(contents)
                .inquiryImages(new ArrayList<>())
                .member(member)
                .fridge(fridge)
                .build();
    }
}
