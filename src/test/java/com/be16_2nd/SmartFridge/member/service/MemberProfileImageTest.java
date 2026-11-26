package com.be16_2nd.SmartFridge.member.service;

import com.be16_2nd.SmartFridge.common.service.S3Uploader;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.fridge.repository.FridgeMemberRepository;
import com.be16_2nd.SmartFridge.member.dto.UpdateProfielImageDto;
import com.be16_2nd.SmartFridge.member.repository.MemberRepository;
import com.be16_2nd.SmartFridge.notification.repository.NotificationSettingRepository;
import com.be16_2nd.SmartFridge.post.repository.PostCommentRepository;
import com.be16_2nd.SmartFridge.post.repository.PostLikeRepository;
import com.be16_2nd.SmartFridge.post.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 프로필 이미지 변경 요청에 파일이 없을 때 500 대신 입력 오류(IllegalArgumentException → 400)로 끝나는지 확인한다.
 */
class MemberProfileImageTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final S3Uploader s3Uploader = mock(S3Uploader.class);
    private final MemberService memberService = new MemberService(
            memberRepository,
            mock(PasswordEncoder.class),
            s3Uploader,
            mock(PostRepository.class),
            mock(PostLikeRepository.class),
            mock(PostCommentRepository.class),
            mock(NotificationSettingRepository.class),
            mock(FridgeMemberRepository.class),
            mock(FoodRepository.class));

    @Test
    void 파일을_보내지_않으면_입력_오류로_거절한다() {
        UpdateProfielImageDto dto = new UpdateProfielImageDto();

        assertThatThrownBy(() -> memberService.updateMyProfileImage(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("변경할 프로필 이미지를 선택해 주세요.");

        verify(s3Uploader, never()).upload(any());
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 빈_파일을_보내면_입력_오류로_거절한다() {
        UpdateProfielImageDto dto = new UpdateProfielImageDto();
        dto.setNewProfileImage(new MockMultipartFile("newProfileImage", "a.png", "image/png", new byte[0]));

        assertThatThrownBy(() -> memberService.updateMyProfileImage(dto))
                .isInstanceOf(IllegalArgumentException.class);

        verify(s3Uploader, never()).upload(any());
    }
}
