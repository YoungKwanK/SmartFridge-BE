package com.be16_2nd.SmartFridge.food.repository;

import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.fridge.domain.Fridge;
import com.be16_2nd.SmartFridge.member.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface FoodRepository extends JpaRepository<Food, Long> {
    Page<Food> findAll(Specification<Food> specification, Pageable pageable);

    // 멤버가 냉장고를 떠날 때(나가기, 강퇴) 그 멤버의 식품을 삭제하지 않고 등록자 없음 + 공유 식품으로 남김
    // 엔티티 목록(member.getFoods())을 거치지 않고 DB를 바로 바꾸므로 Member의 orphanRemoval로 삭제되지 않음
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Food f SET f.member = null, f.isShared = true WHERE f.fridge = :fridge AND f.member = :member")
    void releaseAllByFridgeAndMember(@Param("fridge") Fridge fridge, @Param("member") Member member);

    // 회원 탈퇴 시 모든 냉장고에서 그 회원의 식품을 등록자 없음 + 공유 식품으로 남김
    // 회원 삭제 전에 호출해야 Member의 cascade로 식품이 함께 삭제되지 않음
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Food f SET f.member = null, f.isShared = true WHERE f.member = :member")
    void releaseAllByMember(@Param("member") Member member);

    // 등록자 없는 식품은 유통기한 알림 받을 사람이 없어 제외 (알림 쪽에서 관리자 발송 처리 전까지 임시)
    @Query("SELECT f FROM Food f WHERE FUNCTION('DATE', f.expirationDateTime) IN :dates AND f.member IS NOT NULL")
    List<Food> findAllByExpirationDateIn(@Param("dates") List<LocalDate> dates);

    // --- MANAGER용 메서드 ---
    long countByFridgeId(Long fridgeId);
    long countByFridgeIdAndExpirationDateTimeBefore(Long fridgeId, LocalDateTime now);
    long countByFridgeIdAndExpirationDateTimeBetween(Long fridgeId, LocalDateTime start, LocalDateTime end);

    // --- COMMON 사용자용 메서드 ---
    long countByFridgeIdAndMemberId(Long fridgeId, UUID userId);
    long countByFridgeIdAndMemberIdAndExpirationDateTimeBefore(Long fridgeId, UUID userId, LocalDateTime now);
    long countByFridgeIdAndMemberIdAndExpirationDateTimeBetween(Long fridgeId, UUID userId, LocalDateTime start, LocalDateTime end);

}
