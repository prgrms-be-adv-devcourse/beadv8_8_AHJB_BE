package com.jjinmak.back.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException e){
        ErrorResponse response = new ErrorResponse(false, e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException e){
        ErrorResponse response = new ErrorResponse(false, e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(MethodArgumentNotValidException e){
        ErrorResponse response = new ErrorResponse(false, "COMMON001", "요청 내용이 잘못됐습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(HttpMessageNotReadableException e){
        ErrorResponse response = new ErrorResponse(false, "COMMON001", "요청 내용이 잘못됐습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException e){
        ErrorResponse response = new ErrorResponse(false, e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handle(BusinessException e){
        ErrorCode code = e.getErrorCode();
        ErrorResponse response = new ErrorResponse(false, code.getCode(), code.getMessage());
        return ResponseEntity.status(code.getStatus()).body(response);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("낙관적 락 충돌", e);
        ErrorCode code = CommonErrorCode.CONFLICT;
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(false, code.getCode(), code.getMessage()));
    }

    /**
     * DB 제약 위반(CHECK·UNIQUE·FK 등). 코드 버그나 스키마 불일치라 500 으로 응답한다.
     * 여기서 잡지 않으면 Spring Boot 가 /error 로 forward 하고, 그 경로가 인증 필요라 Security 가 401(COMMON002)을 써 버려
     * 클라이언트가 "토큰 만료"로 오해한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("DB 제약 위반", e);
        ErrorCode code = CommonErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(false, code.getCode(), code.getMessage()));
    }

    /** 위에서 잡지 못한 모든 예외. /error forward → 401 둔갑을 막기 위해 공통 응답 형식의 500 으로 닫는다 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        ErrorCode code = CommonErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(false, code.getCode(), code.getMessage()));
    }
}
