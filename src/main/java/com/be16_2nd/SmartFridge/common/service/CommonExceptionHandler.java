package com.be16_2nd.SmartFridge.common.service;

import com.be16_2nd.SmartFridge.agent.exception.AgentActionException;
import com.be16_2nd.SmartFridge.agent.exception.AgentChatException;
import com.be16_2nd.SmartFridge.common.ai.AiException;
import com.be16_2nd.SmartFridge.common.dto.CommonErrorDto;
import com.be16_2nd.SmartFridge.inquiry.exception.InquiryConflictException;
import com.be16_2nd.SmartFridge.receipt.exception.ReceiptException;
import com.be16_2nd.SmartFridge.recipe.service.RecipeImportInProgressException;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.AuthorizationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;

@Slf4j
@ControllerAdvice
public class CommonExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> illergalException(IllegalArgumentException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.BAD_REQUEST.value(), e.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> noSuchException(NoSuchElementException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.NOT_FOUND.value(), e.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> methodArgumentNotValidException(MethodArgumentNotValidException e){
        log.error(e.getMessage());
        String errorMessage = e.getBindingResult().getFieldError().getDefaultMessage();
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.BAD_REQUEST.value(), errorMessage), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> entityNotFoundException(EntityNotFoundException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.NOT_FOUND.value(), e.getMessage()), HttpStatus.NOT_FOUND);
    }

    // ControllerAdvice가 먼저 잡기 때문에 별도 처리가 없으면 Exception 처리기로 넘어가 500이 된다
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> accessDeniedException(AccessDeniedException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.FORBIDDEN.value(), e.getMessage()), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthorizationServiceException.class)
    public ResponseEntity<?> authorizationServiceException(AuthorizationServiceException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.UNAUTHORIZED.value(), e.getMessage()), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(InquiryConflictException.class)
    public ResponseEntity<?> inquiryConflictException(InquiryConflictException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.CONFLICT.value(), e.getMessage()), HttpStatus.CONFLICT);
    }

    // @Version 충돌: 다른 사람이 같은 데이터를 먼저 고치거나 지웠다
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<?> optimisticLockingFailureException(OptimisticLockingFailureException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.CONFLICT.value(),
                "다른 멤버가 먼저 수정하거나 정리했어요. 목록을 새로 고친 뒤 다시 시도해 주세요."), HttpStatus.CONFLICT);
    }

    // AI 호출 실패: 원인에 맞는 상태 코드(502/503)를 예외가 들고 있다
    @ExceptionHandler(AiException.class)
    public ResponseEntity<?> aiException(AiException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(e.getStatus().value(), e.getMessage()), e.getStatus());
    }

    @ExceptionHandler(RecipeSourceException.class)
    public ResponseEntity<?> recipeSourceException(RecipeSourceException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.BAD_GATEWAY.value(), e.getMessage()), HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(RecipeImportInProgressException.class)
    public ResponseEntity<?> recipeImportInProgressException(RecipeImportInProgressException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.CONFLICT.value(), e.getMessage()), HttpStatus.CONFLICT);
    }

    // AI 에이전트 제안을 확인·취소할 수 없는 상태: 이미 처리됨(409), 유효 시간 지남(410)
    @ExceptionHandler(AgentActionException.class)
    public ResponseEntity<?> agentActionException(AgentActionException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(e.getStatus().value(), e.getMessage()), e.getStatus());
    }

    // AI 도우미 채팅을 쓸 수 없는 상태: 하루 사용 횟수 초과(429)
    @ExceptionHandler(AgentChatException.class)
    public ResponseEntity<?> agentChatException(AgentChatException e){
        log.error(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(e.getStatus().value(), e.getMessage()), e.getStatus());
    }

    // 영수증 인식을 쓸 수 없는 상태: 하루 사용 횟수 초과(429)
    @ExceptionHandler(ReceiptException.class)
    public ResponseEntity<?> receiptException(ReceiptException e){
        log.warn(e.getMessage());
        return new ResponseEntity<>(new CommonErrorDto(e.getStatus().value(), e.getMessage()), e.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> exception(Exception e){
        log.error(e.getMessage());
        String errorMessage = e.getMessage();
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.INTERNAL_SERVER_ERROR.value(), errorMessage), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
