#!/bin/bash
# .env 파일에서 환경변수 로드 후 서버 기동
set -a
source "$(dirname "$0")/.env"
set +a

./gradlew bootRun --args='--spring.profiles.active=local'
