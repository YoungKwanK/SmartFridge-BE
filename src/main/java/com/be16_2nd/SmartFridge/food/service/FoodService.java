package com.be16_2nd.SmartFridge.food.service;

import com.be16_2nd.SmartFridge.common.service.FridgeAccessValidator;
import com.be16_2nd.SmartFridge.fridge.domain.Type;
import com.be16_2nd.SmartFridge.food.domain.Food;
import com.be16_2nd.SmartFridge.food.domain.FoodHistory;
import com.be16_2nd.SmartFridge.food.dto.*;
import com.be16_2nd.SmartFridge.food.repository.FoodHistoryRepository;
import com.be16_2nd.SmartFridge.food.repository.FoodRepository;
import com.be16_2nd.SmartFridge.member.domain.Member;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class FoodService {

    private final FoodRepository foodRepository;
    private final FoodHistoryRepository foodHistoryRepository;
    private final FridgeAccessValidator fridgeAccessValidator;

    private static final int MAX_REGISTER_COUNT = 30;

    public List<FoodResDto> registerFood(Long fridgeId, List<FoodCreateDto> foodCreateDtoList) {
        if (foodCreateDtoList == null || foodCreateDtoList.isEmpty()) {
            throw new IllegalArgumentException("등록할 식품이 없습니다.");
        }
        if (foodCreateDtoList.size() > MAX_REGISTER_COUNT) {
            throw new IllegalArgumentException("식품은 한 번에 최대 " + MAX_REGISTER_COUNT + "개까지 등록할 수 있습니다.");
        }

        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        List<Food> foods = foodCreateDtoList.stream()
                .map(dto -> dto.toEntity(context.member(), context.fridge()))
                .toList();
        List<Food> savedFoods = foodRepository.saveAll(foods);

        return savedFoods.stream().map(FoodResDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public Page<FoodResDto> getFoodsByRole(Long fridgeId, Pageable pageable, FoodSearchDto foodSearchDto, Boolean isShared) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);

        Specification<Food> specification = createFoodSpecification(fridgeId, context.type(), context.member(), isShared, foodSearchDto);

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "expirationDateTime"));


        return foodRepository.findAll(specification, sortedPageable).map(FoodResDto::fromEntity);
    }


    public FoodResDto updateFood(Long fridgeId, Long foodId, FoodUpdateDto foodUpdateDto) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        Food food = findHandleableFood(context, fridgeId, foodId);
        Food updatedFood = food.updateFood(foodUpdateDto);
        return FoodResDto.fromEntity(updatedFood);
    }

    // 잘못 등록한 식품을 지울 때 사용. 기록을 남기지 않는다.
    public void deleteFood(Long fridgeId, Long foodId) {
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        Food food = findHandleableFood(context, fridgeId, foodId);
        foodRepository.delete(food);
    }

    // 다 먹었거나 버린 식품을 기록으로 남기고 냉장고에서 뺀다.
    public void finishFood(Long fridgeId, Long foodId, FoodFinishDto foodFinishDto) {
        if (foodFinishDto == null || foodFinishDto.getResult() == null) {
            throw new IllegalArgumentException("처리 결과(소비 또는 폐기)를 선택해 주세요.");
        }
        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        Food food = findHandleableFood(context, fridgeId, foodId);

        foodHistoryRepository.save(FoodHistory.of(food, foodFinishDto.getResult(), LocalDateTime.now()));
        foodRepository.delete(food);
    }

    /**
     * 식품을 찾고, 요청한 회원이 이 식품을 수정·삭제·처리할 수 있는지 확인한다.
     * 식품 목록에 보이는 범위와 같다: 관리자는 전부, 일반 멤버는 공유식품과 자기 식품만.
     */
    private Food findHandleableFood(FridgeAccessValidator.FridgeContext context, Long fridgeId, Long foodId) {
        Food food = foodRepository.findById(foodId)
                .orElseThrow(() -> new EntityNotFoundException("식품을 찾을 수 없습니다. ID: " + foodId));
        if (!food.getFridge().getId().equals(fridgeId)) {
            throw new IllegalArgumentException("해당 냉장고에 존재하지 않는 식품입니다.");
        }
        if (!canHandle(context, food)) {
            throw new AccessDeniedException("다른 멤버의 비공유 식품은 수정하거나 삭제할 수 없습니다.");
        }
        return food;
    }

    private boolean canHandle(FridgeAccessValidator.FridgeContext context, Food food) {
        if (context.type() == Type.MANAGER) {
            return true;
        }
        if (Boolean.TRUE.equals(food.getIsShared())) {
            return true;
        }
        Member owner = food.getMember();
        return owner != null && owner.getId().equals(context.member().getId());
    }

    @Transactional(readOnly = true)
    public FoodStatResDto foodStats(Long fridgeId) {

        FridgeAccessValidator.FridgeContext context = fridgeAccessValidator.validate(fridgeId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysLater = now.plusDays(3);

        long totalCount;
        long expiredCount;
        long expiringSoonCount;

        // 사용자의 역할을 확인합니다.
        if (context.type() == Type.MANAGER) {
            // MANAGER는 냉장고의 모든 음식 통계를 조회합니다.
            totalCount = foodRepository.countByFridgeId(fridgeId);
            expiredCount = foodRepository.countByFridgeIdAndExpirationDateTimeBefore(fridgeId, now);
            expiringSoonCount = foodRepository.countByFridgeIdAndExpirationDateTimeBetween(fridgeId, now, threeDaysLater);
        } else {
            // COMMON 사용자는 자신이 등록한 음식 통계만 조회합니다.
            UUID memberId = context.member().getId();
            totalCount = foodRepository.countByFridgeIdAndMemberId(fridgeId, memberId);
            expiredCount = foodRepository.countByFridgeIdAndMemberIdAndExpirationDateTimeBefore(fridgeId, memberId, now);
            expiringSoonCount = foodRepository.countByFridgeIdAndMemberIdAndExpirationDateTimeBetween(fridgeId, memberId, now, threeDaysLater);
        }

    long freshCount = totalCount - expiredCount - expiringSoonCount;

    return FoodStatResDto.builder()
            .totalCount(totalCount)
            .freshCount(freshCount)
            .expiringSoonCount(expiringSoonCount)
            .expiredCount(expiredCount)
            .build();
}

    private Specification<Food> createFoodSpecification(Long fridgeId, Type userType, Member member, Boolean isShared, FoodSearchDto searchDto) {
        return (root, query, cb) -> {
            List<Predicate> predicateList = new ArrayList<>();
            predicateList.add(cb.equal(root.get("fridge").get("id"), fridgeId));

            if (userType == Type.MANAGER && searchDto.getViewAll()) {

            } else {
                Predicate isSharedPredicate = cb.equal(root.get("isShared"), true);
                Predicate isMyFoodPredicate = cb.equal(root.get("member"), member);
                predicateList.add(cb.or(isSharedPredicate, isMyFoodPredicate));
            }

            if (isShared != null) {
                predicateList.add(cb.equal(root.get("isShared"), isShared));
            }
            if (searchDto.getCategory() != null) {
                predicateList.add(cb.equal(root.get("category"), searchDto.getCategory()));
            }
            if (StringUtils.hasText(searchDto.getFoodName())) {
                predicateList.add(cb.like(root.get("name"), "%" + searchDto.getFoodName() + "%"));
            }
            // 유통기한 필터
            addFilterPredicate(predicateList, searchDto.getFilterType(), cb, root);

            return cb.and(predicateList.toArray(new Predicate[0]));
        };
    }

    private void addFilterPredicate(List<Predicate> predicates, FoodFilterType filterType, CriteriaBuilder cb, Root<Food> root) {
        if (filterType == null || filterType == FoodFilterType.ALL) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        switch (filterType) {
            case EXPIRED:
                predicates.add(cb.lessThan(root.get("expirationDateTime"), now));
                break;
            case EXPIRES_TODAY:
                LocalDateTime startOfToday = today.atStartOfDay();
                LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();
                predicates.add(cb.between(root.get("expirationDateTime"), startOfToday, endOfToday));
                break;
            case EXPIRES_IN_THREE_DAYS:
                LocalDateTime threeDaysLater = today.plusDays(3).atStartOfDay();
                predicates.add(cb.between(root.get("expirationDateTime"), now, threeDaysLater));
                break;
        }
    }
}