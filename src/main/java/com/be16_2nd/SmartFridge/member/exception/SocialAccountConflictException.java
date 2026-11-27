package com.be16_2nd.SmartFridge.member.exception;

import com.be16_2nd.SmartFridge.member.domain.SocialType;
import lombok.Getter;

// 소셜 로그인한 이메일이 이미 다른 소셜 계정으로 가입돼 있을 때 발생 (409)
// 프론트가 문구를 파싱하지 않고 기존 제공자로 이어서 로그인시킬 수 있도록 제공자를 함께 담는다
@Getter
public class SocialAccountConflictException extends RuntimeException {

    public static final String CODE = "SOCIAL_ACCOUNT_EXISTS";

    private final SocialType registeredProvider;

    public SocialAccountConflictException(SocialType registeredProvider) {
        super("이미 " + registeredProvider.getDisplayName() + "(으)로 가입된 이메일입니다. "
                + registeredProvider.getDisplayName() + "(으)로 로그인해주세요.");
        this.registeredProvider = registeredProvider;
    }
}
