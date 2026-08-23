#!/bin/bash
# mes_server 홈서버 배포 헬퍼 — runner 유저가 sudo로 실행하는 유일한 진입점.
# 설치: sudo install -o root -g root -m 755 deploy/home/mes-deploy.sh /usr/local/bin/mes-deploy
# 배포 입력은 러너 워크스페이스가 아닌 root 소유 REPO_DIR(origin/main 직접 체크아웃)만 사용
# — runner가 쓸 수 있는 파일을 root가 실행하는 경로를 차단(compose 변조 → root 승격 방지).
set -euo pipefail
[ $# -eq 1 ] || { echo "usage: mes-deploy {up|ps|prune|diagnose}" >&2; exit 64; }
REPO_DIR=/opt/deploy/mes
ENV_FILE=/home/jb/srv/mes/.env
LOG_DIR=/var/log/mes-deploy
COMPOSE=(docker compose -f "$REPO_DIR/deploy/home/compose.yml" --env-file "$ENV_FILE")
case "$1" in
  up)
    git -C "$REPO_DIR" fetch --depth 1 origin main
    git -C "$REPO_DIR" checkout --detach --force FETCH_HEAD
    exec "${COMPOSE[@]}" up -d --build ;;
  ps)    exec "${COMPOSE[@]}" ps ;;
  prune) exec docker image prune -f --filter "until=72h" ;;
  diagnose)
    # 전체 로그는 호스트에만 저장(root 600) — stdout(공개 Actions 로그)에는 상태 요약만 출력한다
    mkdir -p "$LOG_DIR" && chmod 700 "$LOG_DIR"
    "${COMPOSE[@]}" logs --tail=200 > "$LOG_DIR/last-failure.log" 2>&1 || true
    chmod 600 "$LOG_DIR/last-failure.log" || true
    "${COMPOSE[@]}" ps || true
    # 서비스 키는 mes-app / simulator 다(home-infra#15 — 공유망 일반명 충돌 회피).
    # container_name 은 mes/mes-simulator 로 고정이라 터널 경로는 그대로다.
    APP_ID="$("${COMPOSE[@]}" ps -q mes-app 2>/dev/null || true)"
    if [ -n "$APP_ID" ]; then
      docker inspect "$APP_ID" --format 'app health: {{json .State.Health.Status}}' || true
    fi
    SIM_ID="$("${COMPOSE[@]}" ps -q simulator 2>/dev/null || true)"
    if [ -n "$SIM_ID" ]; then
      docker inspect "$SIM_ID" --format 'simulator state: {{json .State.Status}}' || true
    fi
    echo "full logs saved on host: $LOG_DIR/last-failure.log" ;;
  *)     echo "usage: mes-deploy {up|ps|prune|diagnose}" >&2; exit 64 ;;
esac
