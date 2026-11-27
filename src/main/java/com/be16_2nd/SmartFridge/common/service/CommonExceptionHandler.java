package com.be16_2nd.SmartFridge.common.service;

import com.be16_2nd.SmartFridge.agent.exception.AgentActionException;
import com.be16_2nd.SmartFridge.agent.exception.AgentChatException;
import com.be16_2nd.SmartFridge.common.ai.AiException;
import com.be16_2nd.SmartFridge.common.dto.CommonDto;
import com.be16_2nd.SmartFridge.common.dto.CommonErrorDto;
import com.be16_2nd.SmartFridge.inquiry.exception.InquiryConflictException;
import com.be16_2nd.SmartFridge.member.exception.SocialAccountConflictException;
import com.be16_2nd.SmartFridge.receipt.exception.ReceiptException;
import com.be16_2nd.SmartFridge.recipe.service.RecipeImportInProgressException;
import com.be16_2nd.SmartFridge.recipe.source.RecipeSourceException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.AuthorizationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.sql.SQLException;
import java.util.Map;
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

    // 같은 이메일이 다른 소셜 계정으로 가입돼 있음: 프론트가 기존 제공자로 이어서 로그인시킬 수 있게 코드와 제공자를 담는다
    @ExceptionHandler(SocialAccountConflictException.class)
    public ResponseEntity<?> socialAccountConflictException(SocialAccountConflictException e){
        log.warn(e.getMessage());
        Map<String, String> result = Map.of(
                "code", SocialAccountConflictException.CODE,
                "provider", e.getRegisteredProvider().name(),
                "providerName", e.getRegisteredProvider().getDisplayName());
        return new ResponseEntity<>(CommonDto.builder()
                .result(result)
                .status_code(HttpStatus.CONFLICT.value())
                .status_message(e.getMessage())
                .build(), HttpStatus.CONFLICT);
    }

    // @Version 충돌: 다른 사람이 같은 데이터를 먼저 고치거나 지웠다
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<?> optimisticLockingFailureException(OptimisticLockingFailureException e){
        log.error(e.getMessage());
        return concurrentUpdateConflict();
    }

    // MariaDB 스냅샷 격리 충돌(오류 1020 "Record has changed since last read").
    // 같은 행을 동시에 고치면 @Version 검사보다 DB가 먼저 막는데, 스프링이 이를 낙관적 락 실패로 알아보지 못하고
    // 일반 DB 오류로 감싸서 올려 보낸다. 원인을 따라가 1020이면 @Version 충돌과 같은 409로 돌려주고, 아니면 기존처럼 500
    @ExceptionHandler({DataAccessException.class, TransactionSystemException.class, PersistenceException.class})
    public ResponseEntity<?> dataAccessException(Exception e){
        if (isSnapshotConflict(e)) {
            log.warn("동시 수정 충돌(MariaDB 1020): {}", e.getMessage());
            return concurrentUpdateConflict();
        }
        return exception(e);
    }

    private static final int MARIADB_RECORD_CHANGED = 1020;

    static boolean isSnapshotConflict(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == MARIADB_RECORD_CHANGED) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<?> concurrentUpdateConflict() {
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

    static final String INTERNAL_ERROR_MESSAGE = "서버에서 오류가 발생했어요. 잠시 후 다시 시도해 주세요.";
    static final String CLIENT_ERROR_MESSAGE = "요청을 처리할 수 없어요. 입력한 내용을 확인해 주세요.";

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> exception(Exception e){
        // 파라미터 누락·잘못된 본문·지원하지 않는 메서드 같은 스프링 요청 오류는 원래 상태 코드(4xx)로 돌려준다
        if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            log.warn(e.getMessage());
            HttpStatusCode status = errorResponse.getStatusCode();
            String detail = errorResponse.getBody().getDetail();
            return new ResponseEntity<>(new CommonErrorDto(status.value(), detail != null ? detail : CLIENT_ERROR_MESSAGE), status);
        }
        // 예외 메시지에는 SQL·클래스 이름 같은 내부 정보가 담길 수 있어 응답에는 고정 문구만 보내고, 원인은 로그로 남긴다
        log.error("처리하지 못한 예외", e);
        return new ResponseEntity<>(new CommonErrorDto(HttpStatus.INTERNAL_SERVER_ERROR.value(), INTERNAL_ERROR_MESSAGE), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
