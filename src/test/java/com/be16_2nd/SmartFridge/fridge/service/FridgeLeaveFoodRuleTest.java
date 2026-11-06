package com.be16_2nd.SmartFridge.fridge.service;

import com.be16_2nd.SmartFridge.chat.service.ChatService;
import com.be16_2nd.SmartFridge.chat.service.ManagerChatRoomLifecycle;
import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.food.domain.Category;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.domain.StorageType;
import com.be16_2nd.SmartFridge.food.dto.FoodResDto;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.fridge.domain.FridgeMember;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.fridge.repository.FridgeMemberRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.member.repository.MemberRepository;
import com.be16_2nd.SmartFridge.member.service.AdminService;
import com.be16_2nd.SmartFridge.member.service.MemberService;
import com.be16_2nd.SmartFridge.post.repository.PostRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 멤버가 냉장고를 떠날 때(나가기, 강퇴, 회원 탈퇴, 관리자 강제 탈퇴) 식품을 삭제하지 않고
 * 등록자 없음 + 공유 식품으로 남기는지 확인하는 단위 테스트.
 * 스프링과 DB를 띄우지 않고 가짜 객체(Mock)로만 확인하므로 로컬 DB에 영향이 없다.
 */
@ExtendWith(MockitoExtension.class)
class FridgeLeaveFoodRuleTest {

    @Mock FridgeAccessValidator fridgeAccessValidator;
    @Mock FridgeMemberRepository fridgeMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock FoodRepository foodRepository;
    @Mock PostRepository postRepository;
    @Mock ManagerChatRoomLifecycle managerChatRoomLifecycle;
    @Mock ChatService chatService;

    @InjectMocks FridgeService fridgeService;
    @InjectMocks MemberService memberService;
    @InjectMocks AdminService adminService;

    final Long fridgeId = 1L;
    final Fridge fridge = Fridge.builder().id(fridgeId).fridgeName("우리집 냉장고").build();
    final Member manager = Member.builder().name("관리자").email("manager@test.com").build();
    final Member member = Member.builder().name("홍길동").email("user@test.com").build();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 나가기를_하면_식품을_삭제하지_않고_남긴다() {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, member, Type.COMMON));

        fridgeService.leaveFridge(fridgeId);

        verify(foodRepository).releaseAllByFridgeAndMember(fridge, member);
        verify(foodRepository, never()).deleteAll(any());
        // 게시글 삭제는 기존 그대로
        verify(postRepository).deleteAllByFridgeAndMember(fridge, member);
    }

    @Test
    void 관리자는_나갈_수_없고_식품도_그대로다() {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, manager, Type.MANAGER));

        assertThatThrownBy(() -> fridgeService.leaveFridge(fridgeId))
                .hasMessage("COMMON만 나갈수 있습니다");

        verify(foodRepository, never()).releaseAllByFridgeAndMember(any(), any());
    }

    @Test
    void 강퇴를_하면_강퇴된_멤버의_식품을_남긴다() {
        when(fridgeAccessValidator.validate(fridgeId))
                .thenReturn(new FridgeAccessValidator.FridgeContext(fridge, manager, Type.MANAGER));
        when(memberRepository.findByEmail("user@test.com")).thenReturn(Optional.of(member));
        FridgeMember fridgeMember = FridgeMember.builder().fridge(fridge).member(member).type(Type.COMMON).build();
        when(fridgeMemberRepository.findByFridgeAndMember(fridge, member)).thenReturn(Optional.of(fridgeMember));

        fridgeService.removeMember(fridgeId, "user@test.com");

        verify(fridgeMemberRepository).delete(fridgeMember);
        verify(foodRepository).releaseAllByFridgeAndMember(fridge, member);
    }

    @Test
    void 회원_탈퇴를_하면_회원을_지우기_전에_모든_냉장고의_식품을_남긴다() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("user@test.com", null, List.of()));
        when(memberRepository.findByEmail("user@test.com")).thenReturn(Optional.of(member));
        when(fridgeMemberRepository.findAllByMember(member))
                .thenReturn(List.of(FridgeMember.builder().fridge(fridge).member(member).type(Type.COMMON).build()));

        memberService.delete();

        // 순서가 중요: 회원을 먼저 지우면 cascade로 식품까지 삭제됨
        InOrder order = inOrder(foodRepository, memberRepository);
        order.verify(foodRepository).releaseAllByMember(member);
        order.verify(memberRepository).delete(member);
    }

    @Test
    void 관리자인_냉장고가_있으면_탈퇴할_수_없고_식품도_그대로다() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("manager@test.com", null, List.of()));
        when(memberRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(manager));
        when(fridgeMemberRepository.findAllByMember(manager))
                .thenReturn(List.of(FridgeMember.builder().fridge(fridge).member(manager).type(Type.MANAGER).build()));

        assertThatThrownBy(() -> memberService.delete())
                .isInstanceOf(IllegalArgumentException.class);

        verify(foodRepository, never()).releaseAllByMember(any());
        verify(memberRepository, never()).delete(any());
    }

    @Test
    void 관리자_강제_탈퇴도_회원을_지우기_전에_식품을_남긴다() {
        when(memberRepository.findByEmail("user@test.com")).thenReturn(Optional.of(member));

        adminService.deleteMember("user@test.com");

        InOrder order = inOrder(foodRepository, memberRepository);
        order.verify(foodRepository).releaseAllByMember(member);
        order.verify(memberRepository).delete(member);
    }

    @Test
    void 등록자가_없는_식품은_등록자_없음으로_보여준다() {
        FoodResDto dto = FoodResDto.fromEntity(food(null));

        assertThat(dto.getRegisteredName()).isEqualTo("등록자 없음");
    }

    @Test
    void 등록자가_있는_식품은_등록자_이름을_보여준다() {
        FoodResDto dto = FoodResDto.fromEntity(food(member));

        assertThat(dto.getRegisteredName()).isEqualTo("홍길동");
    }

    private Food food(Member owner) {
        return Food.builder()
                .id(10L)
                .name("우유")
                .quantity(1)
                .category(Category.유제품)
                .storageType(StorageType.냉장)
                .storageStartDate(LocalDate.now())
                .expirationDateTime(LocalDateTime.now().plusDays(3))
                .isShared(true)
                .member(owner)
                .fridge(fridge)
                .build();
    }
}
