# 🏭 MES Server

> 🎯 **"설비 센서 데이터를 실시간으로 수집·분석하고, 작업지시·불량 이력을 자동화하는 제조실행시스템(MES) 통합 백엔드"**
>
> 단순한 데이터 저장을 넘어, Redis와 MySQL의 이중 저장소 전략으로 고속 수집과 영구 보관을 동시에 달성했습니다.
>
> Server-Sent Events(SSE) 기반 실시간 푸시, Redis 카운터 기반 1분 단위 배치 집계, Discord 알림까지 실제 운영 환경을 고려한 아키텍처를 구축했습니다.

🔗 실제 서비스 접속해 보기: `https://mes.rkqkdrnportfolio.shop`

🔗 **Swagger API**: `https://mes.rkqkdrnportfolio.shop/swagger-ui.html`

🔗 Discord:https://discord.gg/a9VhVFbqnR



🧪 **테스트 계정**
┣ ID: admin
┣ PW: admin1234
<br>

## 📋 목차

- [Tech Stack & Architecture](#️-tech-stack--architecture)
- [주요 기술적 의사결정 및 트러블슈팅](#-주요-기술적-의사결정-및-트러블슈팅)
- [API 엔드포인트](#-api-엔드포인트)
- [실행 방법](#️-실행-방법)
- [예외 처리](#-예외-처리)

<br>

## 🛠️ Tech Stack & Architecture

### Tech Stack
* **Backend:** Java 21, Spring Boot 3.4, Spring Data JPA, Querydsl 5, Spring Security 6 + JWT
* **Database & Cache:** MySQL 8, Redis
* **Realtime:** Server-Sent Events (SSE)
* **Export:** Apache POI (Excel), OpenCSV
* **Notification:** Discord Webhook
* **Docs:** Springdoc OpenAPI (Swagger UI)
* **CI/CD:** GitHub Actions + EC2 Blue-Green 무중단 배포

### System Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    Client / Browser                      │
└──────────────────────┬───────────────────────────────────┘
                       │ HTTPS
                  ┌────▼────┐
                  │  Nginx  │  (Blue-Green 프록시)
                  └────┬────┘
         ┌─────────────┴─────────────┐
    ┌────▼────┐                 ┌────▼────┐
    │ :8085   │  Blue instance  │ :8086   │  Green instance
    │ Spring  │◄──── 전환 ─────►│ Spring  │
    │  Boot   │                 │  Boot   │
    └────┬────┘                 └─────────┘
         │
  ┌──────┴──────┐
  │             │
┌─▼───┐     ┌──▼──┐
│MySQL│     │Redis│  센서 버퍼 (TTL 60s) + WO 카운터 + Spring Cache
└─────┘     └─────┘

Python Simulator ──POST /api/sensor/data──► Spring Boot (인증 불필요)
                  (3초 간격, 설비 임계값 동적 로드)
```

* **Redis:** 3초마다 쏟아지는 센서 데이터를 메모리에 임시 버퍼링하여 DB 쓰기 부하 차단
* **MySQL:** 1분 평균값 + 작업지시 수량만 영구 저장하여 스토리지 효율 극대화
* **Spring Cache (Redis 백엔드):** 설비별 임계값 설정을 캐시하여 매 요청마다 발생하는 DB 조회 제거
* **SSE:** 롱폴링 없이 서버에서 브라우저로 단방향 실시간 스트리밍

<br>

## 💡 주요 기술적 의사결정 및 트러블슈팅

### 1. ⚡ Redis 카운터 기반 작업지시 수량 집계 (Write-Buffer + Batch Flush)
> **의사결정:** 센서 수신마다 DB에 goodQty/defectQty를 직접 UPDATE하지 않고, Redis INCR로 카운터를 쌓은 뒤 1분 배치로 DB에 일괄 반영

* **🚨 Issue:** 설비 수가 늘어날수록 매 센서 이벤트마다 WorkOrder UPDATE 쿼리가 선형으로 증가
* **💡 Resolution:**
  * **Redis INCR:** 센서 수신 시 `wo:good:{woId}`, `wo:defect:{woId}` 키를 원자적으로 증가 (DB 접근 없음)
  * **wo:active:{equipmentId}:** 활성 작업지시 ID를 Redis에 캐시하여 매 센서마다 IN_PROGRESS WO 조회 쿼리 제거
  * **Batch Flush:** `WorkOrderQtyFlushScheduler`가 1분마다 `wo:good:*` 키를 스캔하여 DB에 일괄 반영 후 Redis 키 삭제
  * **자동 완료:** 계획 수량 달성 시 COMPLETED 전환 + 동일 설비 신규 작업지시 자동 생성
* **📈 성과:** 센서 수신 경로에서 DB 접근을 0회로 감소, 설비 수 증가에도 쓰기 부하 불변

<br>

### 2. 🔄 Spring Cache를 활용한 설비 임계값 캐싱
> **의사결정:** 센서 데이터 수신마다 설비별 임계값을 DB에서 조회하는 반복 I/O를 제거하고자 Redis 백엔드 기반 `@Cacheable` 적용

* **🚨 Issue:** 매 센서 요청마다 EquipmentConfig를 DB에서 조회하면, 설비 수 증가 시 불필요한 SELECT 쿼리가 선형으로 증가
* **💡 Resolution:**
  * **`@Cacheable`:** 설비 설정 최초 조회 시 Redis에 캐싱하여 이후 요청은 DB 접근 없이 처리
  * **`@CacheEvict`:** 설비 설정 변경/삭제 시 캐시 즉시 무효화하여 데이터 정합성 유지
* **📈 성과:** 설정 조회 쿼리를 캐시 히트 시 0회로 감소, 임계값 판정 로직의 응답 속도 개선

<br>

### 3. 📡 Server-Sent Events(SSE) 기반 실시간 모니터링
> **의사결정:** 브라우저에서 최신 센서 데이터를 보여주기 위해 폴링 대신 SSE로 서버 주도 푸시 방식 채택

* **🚨 Issue:** 3초마다 브라우저가 API를 폴링하면 불필요한 HTTP 오버헤드가 발생하고, 다중 탭에서 중복 요청이 급증
* **💡 Resolution:**
  * **SSE 구독:** 브라우저가 `/api/sse/subscribe?equipmentId=X`로 연결을 맺으면 서버가 데이터 수신 시 자동으로 푸시
  * **다중 탭 지원:** `ConcurrentHashMap<equipmentId, CopyOnWriteArrayList<SseEmitter>>` 구조로 동일 설비를 구독 중인 모든 탭에 동시 푸시
  * **30분 타임아웃:** 장시간 연결 유지 시 리소스 누수 방지
* **📈 성과:** 클라이언트 요청 제거로 서버 부하 감소, 센서 수신 즉시 브라우저 반영

<br>

### 4. 🧹 소프트 딜리트 & 데이터 생명주기 관리
> **의사결정:** 설비 삭제 시 관련 이력 데이터를 즉시 하드 삭제하지 않고 소프트 딜리트 후 스케줄러로 단계적 영구 삭제

* **🚨 Issue:** 설비 삭제 직후 대량의 SensorHistory를 한 번에 하드 삭제하면 DB 락 및 응답 지연이 발생할 위험
* **💡 Resolution:**
  * **소프트 딜리트:** 설비 삭제 시 관련 SensorHistory의 `deletedAt` 필드에 타임스탬프만 기록
  * **스케줄러 정리:** 매일 02:00 스케줄러가 30일 경과 데이터를 배치 삭제
  * **데이터 격리:** 소프트 딜리트된 데이터는 조회에서 자동 제외
* **📈 성과:** 설비 삭제 응답 시간 단축, DB 부하 분산 및 스토리지 자동 관리

<br>

### 5. 🚀 Blue-Green 무중단 배포 트러블슈팅
> **상황:** GitHub Actions CI/CD에서 Blue-Green 배포 시 Nginx 포트 전환이 되지 않고 구버전·신버전 서버가 동시에 떠있는 문제 발생

* **🚨 Issue:** 신버전 서버(8081)가 기동됐음에도 Nginx가 구버전(8080)을 계속 바라보고, 구버전 프로세스가 종료되지 않는 현상
* **💡 원인 분석:**
  * 헬스체크 `curl`에 타임아웃 옵션이 없어, 서버가 포트를 열기 전까지 `curl`이 무한 대기 상태 진입
  * SSH 액션 기본 타임아웃(10분) 초과 → 스크립트 강제 종료 → Nginx 전환·구버전 종료 로직이 실행되지 않음
  * 신버전 프로세스는 `nohup &` 백그라운드 실행이라 SSH 세션 종료 후에도 좀비 프로세스로 잔존
* **💡 Resolution:**
  * 헬스체크 `curl`에 `--connect-timeout 3 --max-time 5` 옵션 추가로 빠른 실패 후 재시도 처리
* **📈 성과:** 헬스체크 실패 시 즉시 다음 재시도로 넘어가 SSH 타임아웃 이내에 배포 완료, 포트 전환 정상화

<br>

### 6. 📊 Querydsl 기반 OEE 통계 쿼리 최적화
> **의사결정:** 가동률(Availability)·성능률(Performance)·품질률(Quality) 집계를 애플리케이션 레이어에서 계산하지 않고 DB에 위임하기 위해 Querydsl 도입

* **🚨 Issue:** JPA 메서드 네이밍만으로 집계(sum, avg, count)와 날짜 범위·설비 필터를 조합한 동적 쿼리를 표현하기 어려움
* **💡 Resolution:**
  * **Querydsl Projections:** `QWorkOrderStatisticsDto`, `QSensorStatisticsDto`로 집계 결과를 DTO에 직접 매핑, 엔티티 불필요 조회 제거
  * **BooleanExpression:** 날짜 범위, equipmentId 조건을 타입 안전하게 조합
  * **Repository-Custom-Impl 3단 구조:** JPA 인터페이스와 Querydsl 구현체를 분리하여 유지보수성 확보
* **📈 성과:** OEE 집계 로직 DB 위임으로 애플리케이션 메모리 부하 절감, 컴파일 타임 쿼리 검증으로 런타임 오류 사전 차단

<br>

## 📌 API 엔드포인트

### 센서
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/sensor/data` | ❌ | 센서 데이터 수신 |

### 설비
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/equipment` | ✅ | 설비 목록 조회 |
| POST | `/api/equipment` | ✅ | 설비 등록 |
| DELETE | `/api/equipment/{equipmentId}` | ✅ | 설비 삭제 (소프트 딜리트) |
| GET | `/api/equipment-config/{equipmentId}` | ✅ | 설비 임계값 조회 |
| POST | `/api/equipment-config` | ✅ | 설비 임계값 설정 |

### 작업지시
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/work-orders` | ✅ | 작업지시 목록 조회 |
| POST | `/api/work-orders` | ✅ | 작업지시 등록 |
| PATCH | `/api/work-orders/{id}/status` | ✅ | 상태 전이 |
| GET | `/api/work-orders/{id}/history` | ✅ | 상태 변경 이력 |
| POST | `/api/work-orders/upload` | ✅ | Excel 일괄 등록 |
| GET | `/api/work-orders/template` | ✅ | Excel 템플릿 다운로드 |

### 불량
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/defects` | ✅ | 불량 목록 조회 |
| POST | `/api/defects` | ✅ | 불량 등록 |

### 대시보드
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/dashboard/oee` | ✅ | OEE 통계 조회 |
| GET | `/api/dashboard/sensor-history` | ✅ | 센서 이력 페이징 조회 |
| GET | `/api/dashboard/export/excel` | ✅ | Excel 내보내기 |
| GET | `/api/dashboard/export/csv` | ✅ | CSV 내보내기 |

### 실시간
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/sse/subscribe` | ❌ | SSE 실시간 구독 |

> 📄 **Swagger UI:** `https://www.rkqkdrnportfolio.shop/swagger-ui.html`

<br>

## ⚙️ 실행 방법

### 사전 요구사항
* Java 21
* MySQL — `localhost:3306` / DB: `mes_db`
* Redis — `localhost:6379`

### Docker로 인프라 실행
```bash
# MySQL
docker run -d --name mes-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=mes_db \
  -p 3306:3306 mysql:8.0

# Redis
docker run -d --name mes-redis \
  -p 6379:6379 redis:7
```

### 환경변수
| 변수 | 설명 |
|------|------|
| `DISCORD_WEBHOOK_URL` | Discord 알림 웹훅 URL |

### 실행
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 테스트 계정
애플리케이션 최초 시작 시 자동 생성됩니다.

| 항목 | 값 |
|------|-----|
| username | `admin` |
| password | `admin1234` |

> ⚠️ 로컬 개발 및 테스트 전용 계정입니다. 실제 운영 환경에서는 반드시 변경하세요.

### Python 시뮬레이터 실행
```bash
cd simulator
pip install requests
python simulate.py
```

| 환경변수 | 기본값 | 설명 |
|---------|--------|------|
| `MES_BASE_URL` | `https://www.rkqkdrnportfolio.shop/` | 서버 URL |
| `SENSOR_INTERVAL` | `3` | 전송 주기 (초) |
| `FAULT_RATE` | `0.01` | 이상 데이터 비율 (0.0 ~ 1.0) |
| `RANDOM_SEED` | `42` | 난수 시드 (재현용) |

<br>

## 🚨 예외 처리

| 코드 | HTTP | 설명 |
|------|------|------|
| EQUIPMENT_NOT_FOUND | 404 | 설비 없음 |
| WORK_ORDER_NOT_FOUND | 404 | 작업지시 없음 |
| INVALID_STATUS_TRANSITION | 400 | 허용되지 않는 상태 전이 |
| SENSOR_DATA_NOT_FOUND | 404 | 센서 데이터 없음 |
| INVALID_INPUT_VALUE | 400 | 입력값 오류 |
| DEFECT_NOT_FOUND | 404 | 불량 정보 없음 |
| DEFECT_QTY_EXCEEDS_PLANNED | 400 | 양품 + 불량 수량이 계획 수량 초과 |
| INTERNAL_SERVER_ERROR | 500 | 서버 내부 오류 |

---

최근 업데이트 2026.03.31 — README V1.2.0
