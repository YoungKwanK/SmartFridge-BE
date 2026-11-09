package com.be16_2nd.SmartFridge.inquiry.service;

import com.be16_2nd.SmartFridge.common.service.S3Uploader;
import com.be16_2nd.SmartFridge.inquiry.domain.Inquiry;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryImage;
import com.be16_2nd.SmartFridge.inquiry.domain.InquiryStatus;
import com.be16_2nd.SmartFridge.inquiry.dto.InquiryUpdateDto;
import com.be16_2nd.SmartFridge.inquiry.repository.InquiryRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.member.repository.MemberRepository;
import com.be16_2nd.SmartFridge.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 문의 수정·삭제의 이미지 처리와 첨부 파일 검증 단위 테스트.
 * 스프링과 DB, S3를 사용하지 않고 가짜 객체(Mock)로만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class InquiryImageUpdateTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};

    @Mock InquiryRepository inquiryRepository;
    @Mock MemberRepository memberRepository;
    @Mock NotificationService notificationService;
    @Mock S3Uploader s3Uploader;

    @InjectMocks InquiryService inquiryService;

    final Long inquiryId = 1L;
    final Member owner = Member.builder().id(UUID.randomUUID()).name("작성자").email("owner@test.com").build();
    Inquiry inquiry;

    @BeforeEach
    void setUp() {
        inquiry = Inquiry.builder().inquiryId(inquiryId).title("원래 제목").member(owner).status(InquiryStatus.PENDING).build();
        for (String url : List.of("a", "b", "c")) {
            inquiry.getInquiryImages().add(InquiryImage.builder().imageUrl(url).inquiry(inquiry).build());
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner.getEmail(), null, List.of()));
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 제목만_수정하면_기존_이미지는_그대로_남는다() {
        givenInquiry();

        inquiryService.updateInquiry(inquiryId, updateDto(null));

        assertThat(imageUrls()).containsExactly("a", "b", "c");
        verify(s3Uploader, never()).delete(anyString());
    }

    @Test
    void 앞에서_지운_뒤의_위치로_온_번호를_순서대로_지운다() {
        givenInquiry();

        inquiryService.updateInquiry(inquiryId, updateDto("[0,0]"));

        assertThat(imageUrls()).containsExactly("c");
        verify(s3Uploader).delete("a");
        verify(s3Uploader).delete("b");
        verify(s3Uploader, never()).delete("c");
    }

    @Test
    void 일부를_지우고_새_이미지를_추가할_수_있다() {
        givenInquiry();
        when(s3Uploader.upload(any())).thenReturn("new");
        InquiryUpdateDto dto = updateDto("[1]");
        dto.setImageFiles(List.of(png("new.png")));

        inquiryService.updateInquiry(inquiryId, dto);

        assertThat(imageUrls()).containsExactly("a", "c", "new");
        verify(s3Uploader).delete("b");
    }

    @Test
    void 남는_이미지와_새_이미지가_5장을_넘으면_아무것도_변경하지_않는다() {
        givenInquiry();
        InquiryUpdateDto dto = updateDto(null);
        dto.setImageFiles(List.of(png("1.png"), png("2.png"), png("3.png")));

        assertThatThrownBy(() -> inquiryService.updateInquiry(inquiryId, dto))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(inquiry.getTitle()).isEqualTo("원래 제목");
        assertThat(imageUrls()).containsExactly("a", "b", "c");
        verify(s3Uploader, never()).upload(any());
        verify(s3Uploader, never()).delete(anyString());
    }

    @Test
    void 없는_위치의_이미지를_지우려_하면_거부한다() {
        givenInquiry();

        assertThatThrownBy(() -> inquiryService.updateInquiry(inquiryId, updateDto("[3]")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(s3Uploader, never()).delete(anyString());
    }

    @Test
    void 삭제_번호_형식이_틀리면_거부한다() {
        givenInquiry();

        assertThatThrownBy(() -> inquiryService.updateInquiry(inquiryId, updateDto("0,1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 문의를_삭제하면_S3_이미지도_삭제한다() {
        givenInquiry();

        inquiryService.deleteInquiry(inquiryId);

        verify(inquiryRepository).delete(inquiry);
        verify(s3Uploader).delete("a");
        verify(s3Uploader).delete("b");
        verify(s3Uploader).delete("c");
    }

    @Test
    void 빈_파일은_제외한다() {
        MultipartFile empty = new MockMultipartFile("imageFiles", new byte[0]);

        assertThat(InquiryImageValidator.validate(List.of(empty, png("a.png")), 0)).hasSize(1);
    }

    @Test
    void 이미지가_아닌_파일은_확장자가_png여도_거부한다() {
        MultipartFile fake = new MockMultipartFile("imageFiles", "a.png", "image/png", "<html>".getBytes());

        assertThatThrownBy(() -> InquiryImageValidator.validate(List.of(fake), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 장당_5MB를_넘으면_거부한다() {
        byte[] large = new byte[(int) InquiryImageValidator.MAX_IMAGE_BYTES + 1];
        System.arraycopy(PNG, 0, large, 0, PNG.length);
        MultipartFile file = new MockMultipartFile("imageFiles", "a.png", "image/png", large);

        assertThatThrownBy(() -> InquiryImageValidator.validate(List.of(file), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void givenInquiry() {
        when(inquiryRepository.findById(inquiryId)).thenReturn(Optional.of(inquiry));
        when(memberRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
    }

    private InquiryUpdateDto updateDto(String removedImageIndexes) {
        InquiryUpdateDto dto = new InquiryUpdateDto();
        dto.setTitle("수정된 제목");
        dto.setImageFiles(new ArrayList<>());
        dto.setRemovedImageIndexes(removedImageIndexes);
        return dto;
    }

    private MultipartFile png(String name) {
        return new MockMultipartFile("imageFiles", name, "image/png", PNG);
    }

    private List<String> imageUrls() {
        return inquiry.getInquiryImages().stream().map(InquiryImage::getImageUrl).toList();
    }
}
