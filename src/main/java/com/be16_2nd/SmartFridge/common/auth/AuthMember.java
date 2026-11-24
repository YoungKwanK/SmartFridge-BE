package com.be16_2nd.SmartFridge.common.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/*
 * 로그인한 회원 식별자
 * - JWT subject 와 STOMP 세션 사용자(principal) 이름은 회원 id(UUID) 문자열이다
 * - 이메일은 개인정보라 식별자로 쓰지 않는다 (토큰·응답·Redis 키에 남지 않게)
 */
public final class AuthMember {

    private AuthMember() {
    }

    public static UUID currentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        return parse(authentication.getName());
    }

    // principal 이름(회원 id 문자열)을 UUID 로 바꾼다. 비로그인(anonymousUser) 등 형식이 다르면 거부
    public static UUID parse(String principalName) {
        try {
            return UUID.fromString(principalName);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AccessDeniedException("인증 정보가 올바르지 않습니다.");
        }
    }
}
