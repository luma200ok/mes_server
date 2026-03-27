# MES Server — CLAUDE.md

## 프로젝트 개요

설비 모니터링 + 작업지시 관리 + 품질/불량 관리 + OEE 대시보드를 하나로 통합한 MES(제조실행시스템) 백엔드.
Python 시뮬레이터가 3초마다 POST로 센서 데이터를 전송하고, Redis 버퍼 → MySQL 이관 방식으로 처리.

## 기술 스택

- **언어/플랫폼**: Java 21, Spring Boot 3.4
- **데이터**: Spring Data JPA, Spring Data Redis, MySQL
- **쿼리**: Querydsl 5.1 (Jakarta)
- **보안**: Spring Security 6 + JWT (jjwt 0.12)
- **실시간**: SSE (Server-Sent Events)
- **캐시**: Spring Cache + Redis
- **알림**: Discord Webhook (WebFlux WebClient)
- **빌드**: Gradle (Groovy DSL)
- **API 문서**: Springdoc OpenAPI 2.8 (`/swagger-ui.html`)
- **내보내기**: Apache POI (Excel), OpenCSV

## 데이터 흐름

```
Python Simulator (3초마다)
  → POST /api/sensor/data (인증 불필요)
  → Redis에 최신 1건 저장 (TTL 60s)
  → EquipmentConfig(Redis 캐시)로 임계값 즉시 판정
  → 초과 시 Discord Webhook 알림
  → SSE로 구독자에게 실시간 브로드캐스트

SensorAggregationScheduler (매 1분)
  → Redis sensor:* 전체 읽기
  → 평균값 계산 → SensorHistory MySQL INSERT
  → Redis 키 삭제
```

## 패키지 구조

```
com.mes/
├── domain/
│   ├── equipment/     Equipment, EquipmentConfig, Controller, Service, Repository
│   ├── sensor/        SensorData(Redis), SensorHistory(MySQL), SensorService, SseEmitterService
│   ├── workorder/     WorkOrder, WorkOrderHistory, 상태머신, Controller, Service
│   ├── defect/        Defect, DefectType(Enum), Controller, Service
│   └── dashboard/     DashboardQueryRepository(Querydsl), DashboardService, DashboardController
└── global/
    ├── config/        SecurityConfig, PasswordEncoderConfig, RedisConfig, QuerydslConfig, SwaggerConfig
    ├── exception/     GlobalExceptionHandler, CustomException, ErrorCode(Enum)
    ├── scheduler/     SensorAggregationScheduler, DataCleanupScheduler
    ├── discord/       DiscordWebhookService
    ├── security/      JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl, AuthController
    └── init/          DataInitializer (admin계정 + 샘플 설비 3개)
```

## 레이어 규칙

- **Controller → Service → Repository** 단방향 의존
- Controller는 DTO만 반환, 엔티티 직접 노출 금지
- `@Transactional`은 Service 레이어에서만 사용
- 예외는 `CustomException(ErrorCode)` throw → `GlobalExceptionHandler` 일괄 처리
- 새 에러 케이스는 `ErrorCode` Enum에 추가

## 순환 참조 주의

`PasswordEncoder` 빈은 반드시 `PasswordEncoderConfig`에 정의.
`SecurityConfig`에 정의하면 순환 참조 발생:
> SecurityConfig → JwtAuthenticationFilter → UserDetailsServiceImpl → PasswordEncoder → SecurityConfig

## 빌드 및 실행

```bash
# Querydsl Q클래스 생성 (최초 1회 필수)
./gradlew compileJava
# → src/main/generated/ 에 QEquipment, QWorkOrder, QDefect,
#    QWorkOrderStatisticsDto, QSensorStatisticsDto 생성

# 로컬 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# 전체 빌드
./gradlew build
```

## 환경 프로파일

| 프로파일 | 설정 파일 | 설명 |
|---|---|---|
| `local` | `application-local.yml` | 로컬 개발 (MySQL 3306, Redis 6379) |
| `prod` | `application.yml` | 운영 환경 |

## 초기 데이터 (DataInitializer)

서버 기동 시 자동 생성:
- 관리자 계정: `admin / admin1234`
- 샘플 설비: `EQ-001`(CNC 1호), `EQ-002`(CNC 2호), `EQ-003`(조립 로봇)
- 각 설비에 EquipmentConfig(임계값) 자동 생성

## 도메인 상태머신

### WorkOrder 상태 전이
```
PENDING → IN_PROGRESS → COMPLETED
                      → DEFECTIVE  (불량 등록 시 자동 전이)
```
그 외 전이 시도 → `CustomException(INVALID_STATUS_TRANSITION)`

### Equipment 상태
- `RUNNING` / `STOPPED` / `FAULT`

## 스케줄러

| 스케줄러 | 주기 | 역할 |
|---|---|---|
| `SensorAggregationScheduler` | 매 1분 | Redis → MySQL 집계 저장 |
| `DataCleanupScheduler` | 매일 02:00 | 삭제 설비 관련 SensorHistory 소프트 딜리트 |
| `DataCleanupScheduler` | 매일 03:00 | 30일 경과 SensorHistory 하드 삭제 |

## 인증 규칙

| 엔드포인트 | 인증 |
|---|---|
| `POST /api/sensor/data` | 불필요 (시뮬레이터용) |
| `POST /api/auth/login` | 불필요 |
| `GET /api/sse/subscribe` | 불필요 |
| 나머지 전체 | `Authorization: Bearer {JWT}` 필요 |

## 코딩 규칙

- Lombok `@RequiredArgsConstructor` 생성자 주입, `@Autowired` 금지
- 복잡한 집계 쿼리는 `DashboardQueryRepositoryImpl` (Querydsl)에 작성
- Discord 알림은 `DiscordWebhookService`에서만 발송
- SSE 관리는 `SseEmitterService`에서만 처리
- 문자열 상수 대신 Enum(`WorkOrderStatus`, `EquipmentStatus`, `DefectType`) 사용
