# MES Server

Spring Boot 기반 제조실행시스템(MES) 통합 백엔드.
설비 모니터링 + 작업지시 관리 + 품질/불량 관리 + OEE 대시보드를 하나로 통합합니다.

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                     Client / Browser                    │
└───────────────────────┬─────────────────────────────────┘
                        │ HTTPS
                   ┌────▼────┐
                   │  Nginx  │  (Blue-Green 프록시)
                   └────┬────┘
          ┌─────────────┴─────────────┐
     ┌────▼────┐                 ┌────▼────┐
     │ :8080   │  Blue instance  │ :8081   │  Green instance
     │ Spring  │◄───── 전환 ────►│ Spring  │
     │  Boot   │                 │  Boot   │
     └────┬────┘                 └─────────┘
          │
   ┌──────┴──────┐
   │             │
┌──▼──┐      ┌──▼──┐
│MySQL│      │Redis│  센서 버퍼 (TTL 60s) + Spring Cache
└─────┘      └─────┘

Python Simulator ──POST /api/sensor/data──► Spring Boot (인증 불필요)
                  (3초 간격, 설비 3대)
```

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4 |
| ORM | Spring Data JPA + Querydsl 5 |
| Security | Spring Security 6 + JWT |
| Database | MySQL 8 (영구), Redis (버퍼/캐시) |
| 실시간 | SSE (Server-Sent Events) |
| 알림 | Discord Webhook |
| 빌드 | Gradle |
| CI/CD | GitHub Actions + EC2 Blue-Green |
| API 문서 | Springdoc OpenAPI (Swagger UI) |

---

## 도메인 구조

```
src/main/java/com/mes/
├── domain/
│   ├── equipment/      설비 + 설비 임계값 설정
│   ├── sensor/         센서 데이터(Redis) + 이력(MySQL) + SSE
│   ├── workorder/      작업지시 + 상태머신 + 이력
│   ├── defect/         불량 관리
│   └── dashboard/      OEE 통계 (Querydsl) + Excel/CSV 내보내기
└── global/
    ├── config/         Security, Redis, Querydsl, Swagger
    ├── exception/      GlobalExceptionHandler + ErrorCode
    ├── scheduler/      센서 집계(1분), 소프트딜리트 GC(일배치)
    ├── discord/        Discord Webhook 알림
    ├── security/       JWT, UserDetails
    └── init/           DataInitializer (계정 + 샘플 데이터)
```

---

## 실행 방법

### 사전 조건
- Java 21
- Docker (MySQL + Redis 실행용)

### 1. 인프라 실행

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

### 2. 서버 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

서버 기동 시 자동으로:
- 관리자 계정 생성: `admin / admin1234`
- 샘플 설비 3개 생성: EQ-001, EQ-002, EQ-003

### 3. API 문서 확인

```
http://localhost:8080/swagger-ui.html
```

### 4. JWT 토큰 발급

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin1234"}'
```

---

## Python 시뮬레이터

### 설치

```bash
cd simulator
pip install requests
```

### 실행

```bash
python simulate.py
```

### 환경변수 옵션

| 변수 | 기본값 | 설명 |
|---|---|---|
| `MES_BASE_URL` | `http://localhost:8080` | 서버 URL |
| `SENSOR_INTERVAL` | `3` | 전송 주기(초) |
| `FAULT_RATE` | `0.1` | 이상 데이터 비율 (0.0 ~ 1.0) |
| `RANDOM_SEED` | `42` | 난수 시드 (재현용) |

```bash
# 예: 이상 데이터 30%, 5초 간격
FAULT_RATE=0.3 SENSOR_INTERVAL=5 python simulate.py
```

---

## 주요 API

### 센서
| Method | URL | 설명 |
|---|---|---|
| POST | `/api/sensor/data` | 센서 데이터 수신 (인증 불필요) |

### 설비
| Method | URL | 설명 |
|---|---|---|
| GET | `/api/equipment` | 설비 목록 |
| POST | `/api/equipment` | 설비 등록 |
| DELETE | `/api/equipment/{equipmentId}` | 설비 소프트 딜리트 |

### 작업지시
| Method | URL | 설명 |
|---|---|---|
| GET | `/api/work-orders` | 작업지시 목록 |
| POST | `/api/work-orders` | 작업지시 등록 |
| PATCH | `/api/work-orders/{id}/status` | 상태 전이 |
| GET | `/api/work-orders/{id}/history` | 상태 변경 이력 |

### 불량
| Method | URL | 설명 |
|---|---|---|
| GET | `/api/defects?workOrderId=` | 불량 목록 |
| POST | `/api/defects` | 불량 등록 |

### 대시보드
| Method | URL | 설명 |
|---|---|---|
| GET | `/api/dashboard/oee` | OEE 통계 |
| GET | `/api/dashboard/sensor-history` | 센서 이력 조회 |
| GET | `/api/dashboard/export/excel` | Excel 내보내기 |
| GET | `/api/dashboard/export/csv` | CSV 내보내기 |

### 실시간
| Method | URL | 설명 |
|---|---|---|
| GET | `/api/sse/subscribe?equipmentId=` | SSE 실시간 구독 |

---

## CI/CD (GitHub Actions)

`main` 브랜치 push 시 자동 배포:

1. Gradle 빌드 + 테스트
2. JAR → EC2 SCP 전송
3. Blue-Green 포트 전환 (8080 ↔ 8081)
4. Nginx upstream 변경 + 헬스체크
5. 구 인스턴스 종료

### GitHub Secrets 설정

| Secret | 설명 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP |
| `EC2_USER` | EC2 SSH 사용자 (예: `ec2-user`) |
| `EC2_SSH_KEY` | EC2 SSH 개인키 (PEM) |
