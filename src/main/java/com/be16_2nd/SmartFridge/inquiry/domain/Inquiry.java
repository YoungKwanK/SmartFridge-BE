package com.be16_2nd.SmartFridge.inquiry.domain;

import com.be16_2nd.SmartFridge.common.domain.BaseTimeEntity;
import com.be16_2nd.SmartFridge.inquiry.exception.InquiryConflictException;
import com.be16_2nd.SmartFridge.inquiryComment.domain.InquiryComment;
import com.be16_2nd.SmartFridge.member.domain.Member;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class Inquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inquiryId;

    @Enumerated(EnumType.STRING)
    private InquiryType inquiryType;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String contents;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @OneToOne(mappedBy = "inquiry", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private InquiryComment comments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InquiryStatus status = InquiryStatus.PENDING;

    // 프론트가 삭제할 이미지를 목록 위치로 보내므로 순서를 고정한다
    @OneToMany(mappedBy = "inquiry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("imageId ASC")
    @Builder.Default
    @JsonIgnore
    private List<InquiryImage> inquiryImages = new ArrayList<>();

    public void changeStatus(InquiryStatus nextStatus) {
        if (this.status == nextStatus) {
            return;
        }
        if (!this.status.canTransitTo(nextStatus)) {
            throw new InquiryConflictException(
                    "문의 상태를 " + this.status + "에서 " + nextStatus + "(으)로 변경할 수 없습니다.");
        }
        this.status = nextStatus;
    }

    public boolean isOwner(Member member) {
        return this.member != null && this.member.getId().equals(member.getId());
    }

    // 답변이 끝났거나 취소된 문의는 작성자도 수정할 수 없다.
    public void validateEditable() {
        if (this.status == InquiryStatus.COMPLETED || this.status == InquiryStatus.CANCELLED) {
            throw new InquiryConflictException("답변이 완료되었거나 취소된 문의는 수정할 수 없습니다.");
        }
    }

    // 프론트는 이미지를 하나 지울 때마다 목록을 줄이고 그 시점의 위치를 보내므로, 같은 순서로 하나씩 제거해 찾는다.
    public List<InquiryImage> findImagesToRemove(List<Integer> indexes) {
        List<InquiryImage> remaining = new ArrayList<>(this.inquiryImages);
        List<InquiryImage> removed = new ArrayList<>();
        for (int index : indexes) {
            if (index < 0 || index >= remaining.size()) {
                throw new IllegalArgumentException("삭제할 이미지를 찾을 수 없습니다. 새로고침 후 다시 시도해 주세요.");
            }
            removed.add(remaining.remove(index));
        }
        return removed;
    }

    // 문의 1건에는 답변 1개만 달 수 있고, 답변이 달리면 완료 상태가 된다.
    public void validateReplyable() {
        if (this.comments != null) {
            throw new InquiryConflictException("이미 답변이 등록된 문의입니다.");
        }
        if (this.status == InquiryStatus.CANCELLED) {
            throw new InquiryConflictException("취소된 문의에는 답변할 수 없습니다.");
        }
    }

    public void completeWithReply(InquiryComment comment) {
        this.comments = comment;
        if (this.status != InquiryStatus.COMPLETED) {
            changeStatus(InquiryStatus.COMPLETED);
        }
    }
}
