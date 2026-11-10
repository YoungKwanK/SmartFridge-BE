package com.be16_2nd.SmartFridge.post.domain;

/**
 * 게시판 카테고리. Post 테이블의 category 컬럼에 이름 그대로(예: "공지사항") 저장된다.
 * 공동구매 글의 모집 정보(모집 인원, 구매 링크, 채팅방 연결)는 purchase_post 에 있고 post_id 로 연결된다.
 */
public enum PostCategory {
    공지사항, 자유게시판, 공동구매
}
