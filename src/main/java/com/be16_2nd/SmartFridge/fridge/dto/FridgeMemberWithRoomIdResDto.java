package com.be16_2nd.SmartFridge.fridge.dto;

import java.util.UUID;

import com.be16_2nd.SmartFridge.fridge.domain.FridgeMember;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FridgeMemberWithRoomIdResDto {
    private Long fridgeId;
    private UUID memberId;
    private String memberName;
    private String memberProfileImage;
    private Type type;
    private Long roomId;
    private LocalDateTime joinedTime;
    private Long unReadCount;

    public static FridgeMemberWithRoomIdResDto fromEntity(FridgeMember fridgeMember, Long roomId, Long unReadCount){
        return FridgeMemberWithRoomIdResDto.builder()
                .fridgeId(fridgeMember.getFridge().getId())
                .memberId(fridgeMember.getMember().getId())
                .memberName(fridgeMember.getMember().getName())
                .memberProfileImage(fridgeMember.getMember().getProfileImage())
                .type(fridgeMember.getType())
                .joinedTime(fridgeMember.getCreatedTime())
                .roomId(roomId)
                .unReadCount(unReadCount)
                .build();
    }
}
