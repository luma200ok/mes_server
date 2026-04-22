package com.mes.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C003", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "C004", "접근 권한이 없습니다."),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "만료된 토큰입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A003", "아이디 또는 비밀번호가 올바르지 않습니다."),

    // Equipment
    EQUIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "설비를 찾을 수 없습니다."),
    EQUIPMENT_ID_DUPLICATE(HttpStatus.CONFLICT, "E002", "이미 존재하는 설비 ID입니다."),
    EQUIPMENT_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "E003", "설비 설정을 찾을 수 없습니다."),

    // WorkOrder
    WORK_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "W001", "작업지시를 찾을 수 없습니다."),
    WORK_ORDER_INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "W002", "유효하지 않은 상태 전이입니다."),
    WORK_ORDER_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "W003", "이미 완료된 작업지시입니다."),

    // Defect
    DEFECT_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", "불량 정보를 찾을 수 없습니다."),
    DEFECT_QTY_EXCEEDS_COMPLETED(HttpStatus.BAD_REQUEST, "D002", "불량 수량이 완료 수량을 초과합니다."),
    DEFECT_QTY_EXCEEDS_PLANNED(HttpStatus.BAD_REQUEST, "D003", "양품 + 불량 수량이 계획 수량을 초과합니다."),

    // Sensor
    SENSOR_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "센서 데이터를 찾을 수 없습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "U002", "이미 존재하는 사용자명입니다."),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "A004", "로그인 시도 횟수를 초과했습니다. 15분 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
