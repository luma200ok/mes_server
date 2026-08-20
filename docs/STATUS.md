# MES Server — STATUS

**마지막 갱신일**: 2026-08-20 (PR #9 머지 반영)

## 인프라

| 항목 | 값 |
|---|---|
| 실가동 서버 | **oci-arm2** (`144.24.66.68`, RAM 5.5G / Disk 30G 48%) |
| 백엔드 | `mes.service` (systemd, 네이티브 jar, 포트 **8086**) |
| 시뮬레이터 | `mes-simulator.service` |
| 프론트 | React 정적 → `/var/www/mes-client`, nginx `migrated-mes.conf` |
| DB | MySQL 8 `mes_db` (arm2 로컬, `127.0.0.1:3306` 루프백 바인딩) |
| Cache | Redis 7 (arm2 로컬, `127.0.0.1:6379`) |
| 도메인 | https://mes.luma200ok.com (Cloudflare → arm2 단독) |
| 시크릿 | arm2 `/etc/app-secrets/mes.env` |
| CI/CD | `.github/workflows/deploy.yml` — main push → test → bootJar → scp → `systemctl restart` + `/actuator/health` 체크 |
| 스키마 관리 | **Flyway** (`src/main/resources/db/migration`), 운영 baseline v1, `ddl-auto: validate` |
| 조회 헬퍼 | arm2 `/usr/local/bin/mes` — **미설치(설치 예정)**. `mes schema` / `mes sql "<q>"` / `mes tables` / `mes counts` / `mes health` / `mes log [n]` |

> ⚠️ **arm1 잔재**: `mes.service`(failed) · `/var/www/mes-client`(07-02) · `migrated-mes.conf` 가 남아 있음.
> arm1 origin 직결 시 `/api` 가 502. 정리 필요 (아래 P1).

## 마지막 머지 PR

- **#9** `feat: Flyway 도입 + 운영 스키마 baseline, ddl-auto validate 전환` (2026-08-20, Closes #8)
  - `V1__baseline.sql` = 운영 실덤프 8테이블. 운영은 `type=BASELINE` 기록만, DDL 미실행 확인
  - `ddl-auto: update` → `validate` (전 프로파일)
  - **이후 모든 스키마 변경은 `V{n}__{설명}.sql` 로만. 기존 마이그레이션 수정 금지**
- **#7** `ci: 배포 타깃 arm2 정정 + 테스트 게이트·헬스체크 복구` (2026-08-20, Closes #6)
  - CI가 테스트를 아예 실행하지 않던 상태 복구 (`bootJar -x test` → `test` step 추가)
  - 헬스체크 `/api/auth/login`(405로도 통과) → `/actuator/health` + HTTP 200

## 다음 작업 — 확장 로드맵 (Phase 0~5)

### ✅ P0 — 스키마 관리 기반 (완료, PR #9)
- [x] Flyway 도입 + `mes_db` baseline v1
- [x] `ddl-auto` → `validate` (local/prod 전부), `out-of-order: true`

### P1 — 운영 정리
- [ ] arm1 잔재 제거: `mes.service` disable · `migrated-mes.conf` · `/var/www/mes-client`

### P2 — 데이터 모델
- [ ] 라인/공정 계층 (`line`/`process` 테이블, `equipment.line_id`) — 현재 `location String` 뿐
- [ ] 설비-센서 채널 1:N (`sensor_channel`) — 현재 `SensorHistory` 가 온도/진동/RPM 3컬럼 고정

### P2 — 시계열·수집
- [ ] 인제스트 Redis Stream 전환 + **실제 집계** — `avgTemperature` 가 이름과 달리 마지막 1건 스냅샷
- [ ] `redisTemplate.keys("sensor:*")` 제거 (O(N) 블로킹)
- [ ] `sensor_history` 월 파티셔닝 + cleanup 을 `DROP PARTITION` 으로 전환
- [ ] 디바이스별 인증키 (현재 전역 공유키 1개) + 인제스트 Rate Limit

### P2 — 알람
- [ ] 라이프사이클 신설: severity / acknowledged / assignee / 조치이력 (현재 발송 로그만)
- [ ] 쿨다운을 인메모리 `ConcurrentHashMap` → Redis 이전 (재시작 시 리셋·다중 인스턴스 불가)
- [ ] "N초 지속 시 1건" 지속시간 조건 (현재 단순 상한 초과)

### P2 — 테스트
- [ ] 백엔드 테스트 **1개**(`WorkOrderRolloverTest`) / 프론트 0개 → 도메인별 보강

## 알려진 이슈

| # | 내용 | 상태 |
|---|---|---|
| — | arm1 mes 잔재 (502 origin) | 미해결 |
| — | prod `ddl-auto: update` — 스키마 롤백 불가 | ✅ 해결 (PR #9) |
| — | 배포가 Blue-Green 아닌 `systemctl restart` (다운타임 발생) | 인지됨 |
| #1~#5 | 기존 OPEN 이슈 (모바일 반응형, 보안 강화, 롤오버, 카운팅, 불량 관리) | OPEN |
