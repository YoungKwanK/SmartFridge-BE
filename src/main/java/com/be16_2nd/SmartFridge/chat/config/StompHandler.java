package com.be16_2nd.SmartFridge.chat.config;

import com.be16_2nd.SmartFridge.common.auth.AuthMember;

import com.be16_2nd.SmartFridge.chat.domain.ChatRoomType;
import com.be16_2nd.SmartFridge.chat.service.ChatPresence;
import com.be16_2nd.SmartFridge.chat.service.Validator.ChatRoomParticipantValidator;
import com.be16_2nd.SmartFridge.chat.service.Validator.TokenValidator;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
* Stomp 인터셉터
* - CONNECT : 토큰을 검증하고 사용자를 세션에 묶는다 (이후 프레임은 세션 사용자 기준)
* - SUBSCRIBE : 구독 대상 채팅방의 참여자인지 확인
* - SEND : 인증된 세션만 허용 (참여자 확인은 ChatService.saveMessage)
*/

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private static final Pattern SUBSCRIBE_DESTINATION = Pattern.compile("^/topic/chat/(MANAGER|PURCHASE)/(\\d+)$");

    private final TokenValidator tokenValidator;
    private final ChatRoomParticipantValidator chatRoomParticipantValidator;
    private final ChatPresence chatPresence;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> connect(accessor);
            case SUBSCRIBE -> subscribe(accessor);
            case SEND -> requireUser(accessor);
            case UNSUBSCRIBE -> unsubscribe(accessor);
            default -> { }
        }
        return message;
    }

    private void connect(StompHeaderAccessor accessor) {
        Claims claims = tokenValidator.validateToken(accessor.getFirstNativeHeader("Authorization"));
        // 세션 사용자 이름 = 회원 id(UUID 문자열). 이후 프레임과 presence 모두 이 값으로 회원을 구분한다
        String memberId = AuthMember.parse(claims.getSubject()).toString();
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                memberId, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.get("role")))));
        log.info("stomp connect 토큰 검증 완료");
    }

    private void subscribe(StompHeaderAccessor accessor) {
        String memberId = requireUser(accessor);
        Matcher matcher = SUBSCRIBE_DESTINATION.matcher(Objects.requireNonNullElse(accessor.getDestination(), ""));
        if (!matcher.matches() || accessor.getSubscriptionId() == null) {
            throw new AccessDeniedException("잘못된 구독 요청입니다.");
        }
        ChatRoomType roomType = ChatRoomType.valueOf(matcher.group(1));
        Long roomId = Long.parseLong(matcher.group(2));

        // 채팅방 참여자 확인
        chatRoomParticipantValidator.validateParticipant(AuthMember.parse(memberId), roomId, roomType);
        // 지금 채팅방을 보고 있는 사용자에 추가
        chatPresence.subscribe(accessor.getSessionAttributes(), accessor.getSessionId(), accessor.getSubscriptionId(),
                roomType, roomId, memberId);
    }

    // 구독 해제: 구독 id로 방을 찾아 지금 보고 있는 사용자에서 제거 (연결 종료는 StompEventListener)
    private void unsubscribe(StompHeaderAccessor accessor) {
        chatPresence.unsubscribe(accessor.getSessionAttributes(), accessor.getSessionId(), accessor.getSubscriptionId());
    }

    // CONNECT 때 세션에 묶인 사용자 (없으면 거부)
    private String requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new AccessDeniedException("인증되지 않은 STOMP 세션입니다.");
        }
        return user.getName();
    }
}
