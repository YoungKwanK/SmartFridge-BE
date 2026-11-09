package com.be16_2nd.SmartFridge.member.service;

import com.be16_2nd.SmartFridge.member.domain.Member;
import com.be16_2nd.SmartFridge.member.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 시스템 관리자는 1명이며, 시작할 때 계정을 만드는 InitialDataLoader와 같은 설정값으로 찾는다
@Component
@RequiredArgsConstructor
public class AdminAccountProvider {

    @Value("${app.admin.email}")
    private String adminEmail;

    private final MemberRepository memberRepository;

    public Member getAdmin() {
        return memberRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new EntityNotFoundException("등록되지 않은 관리자입니다."));
    }
}
