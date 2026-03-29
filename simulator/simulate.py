"""
MES 센서 데이터 시뮬레이터
- 설비 3대 (EQ-001, EQ-002, EQ-003)
- 3초마다 POST /api/sensor/data
- 1% 확률로 임계값 초과 이상 데이터 발생
- PENDING 작업지시 자동 IN_PROGRESS 전환
- IN_PROGRESS 작업지시 일정 시간 후 COMPLETED 전환
- RANDOM_SEED 환경변수로 재현 가능
"""

import os
import time
import random
import requests
from datetime import datetime, timedelta
from typing import Optional

# ── 설정 ──────────────────────────────────────────
BASE_URL            = os.getenv("MES_BASE_URL",         "http://localhost:8080")
INTERVAL            = int(os.getenv("SENSOR_INTERVAL",  "3"))       # 센서 전송 주기 (초)
FAULT_RATE          = float(os.getenv("FAULT_RATE",     "0.01"))    # 이상 데이터 비율 (1%)
SEED                = int(os.getenv("RANDOM_SEED",      "42"))
ADMIN_ID            = os.getenv("MES_ADMIN_ID",         "admin")
ADMIN_PW            = os.getenv("MES_ADMIN_PW",         "admin1234")
COMPLETE_AFTER_MIN  = int(os.getenv("COMPLETE_AFTER_MIN", "50"))    # IN_PROGRESS → COMPLETED 전환 기준 (분)

EQUIPMENT_IDS = ["EQ-001", "EQ-002", "EQ-003"]

# 정상 범위
NORMAL = {
    "EQ-001": {"temp": (60, 80),  "vibration": (1.0, 4.0), "rpm": (1500, 2800)},
    "EQ-002": {"temp": (55, 75),  "vibration": (0.8, 3.8), "rpm": (1400, 2600)},
    "EQ-003": {"temp": (50, 70),  "vibration": (0.5, 3.0), "rpm": (1200, 2300)},
}

# 임계값 초과 범위 (이상 데이터)
FAULT = {
    "EQ-001": {"temp": (87, 100), "vibration": (5.5, 8.0), "rpm": (3100, 3800)},
    "EQ-002": {"temp": (83, 95),  "vibration": (5.0, 7.5), "rpm": (2900, 3500)},
    "EQ-003": {"temp": (78, 90),  "vibration": (4.0, 6.5), "rpm": (2600, 3200)},
}
# ─────────────────────────────────────────────────


# ── 인증 ──────────────────────────────────────────
_token: Optional[str] = None

def login() -> bool:
    global _token
    try:
        resp = requests.post(
            f"{BASE_URL}/api/auth/login",
            json={"username": ADMIN_ID, "password": ADMIN_PW},
            timeout=5,
        )
        if resp.status_code == 200:
            _token = resp.json().get("token")
            print(f"[인증] 로그인 성공 (admin)")
            return True
        print(f"[인증] 로그인 실패 status={resp.status_code}")
        return False
    except Exception as e:
        print(f"[인증] 로그인 오류: {e}")
        return False


def auth_headers() -> dict:
    return {"Authorization": f"Bearer {_token}"} if _token else {}
# ─────────────────────────────────────────────────


# ── 백엔드 대기 ────────────────────────────────────
def wait_for_backend(retry_interval: int = 3):
    print(f"[대기] 백엔드 준비 확인 중... ({BASE_URL})")
    attempt = 1
    while True:
        try:
            resp = requests.get(f"{BASE_URL}/api/auth/login", timeout=3)
            print(f"[대기] 백엔드 준비 완료 (시도 {attempt}회)")
            return
        except requests.exceptions.RequestException:
            print(f"  [{attempt}] 백엔드 미응답, {retry_interval}초 후 재시도...")
            attempt += 1
            time.sleep(retry_interval)
# ─────────────────────────────────────────────────


# ── 작업지시 관리 ──────────────────────────────────
def fetch_work_orders():
    """전체 작업지시 조회"""
    try:
        resp = requests.get(
            f"{BASE_URL}/api/work-orders",
            headers=auth_headers(),
            timeout=5,
        )
        if resp.status_code == 200:
            return resp.json()
        if resp.status_code == 401:
            print("[작업지시] 토큰 만료, 재로그인 시도...")
            login()
        return []
    except Exception as e:
        print(f"[작업지시] 조회 오류: {e}")
        return []


def change_work_order_status(wo_id: int, status: str, completed_qty: Optional[int] = None) -> bool:
    """작업지시 상태 전이"""
    body = {"status": status}
    if completed_qty is not None:
        body["completedQty"] = completed_qty
    try:
        resp = requests.patch(
            f"{BASE_URL}/api/work-orders/{wo_id}/status",
            json=body,
            headers=auth_headers(),
            timeout=5,
        )
        return resp.status_code == 200
    except Exception as e:
        print(f"[작업지시] 상태 변경 오류 id={wo_id}: {e}")
        return False


def manage_work_orders():
    """
    PENDING → IN_PROGRESS 자동 시작
    IN_PROGRESS → COMPLETED (COMPLETE_AFTER_MIN 분 경과 시)
    """
    orders = fetch_work_orders()
    if not orders:
        return

    started = completed = 0

    for wo in orders:
        status = wo.get("status")
        wo_id  = wo.get("id")
        eq_id  = wo.get("equipmentId")

        # PENDING → IN_PROGRESS
        if status == "PENDING":
            if change_work_order_status(wo_id, "IN_PROGRESS"):
                started += 1
                print(f"  ▶ [{eq_id}] {wo['workOrderNo']} PENDING → IN_PROGRESS")

        # IN_PROGRESS → COMPLETED (경과 시간 초과)
        elif status == "IN_PROGRESS" and wo.get("startedAt"):
            started_at = datetime.fromisoformat(wo["startedAt"])
            elapsed = datetime.now() - started_at
            if elapsed >= timedelta(minutes=COMPLETE_AFTER_MIN):
                planned = wo.get("plannedQty", 0)
                if change_work_order_status(wo_id, "COMPLETED", planned):
                    completed += 1
                    print(f"  ✅ [{eq_id}] {wo['workOrderNo']} COMPLETED "
                          f"(가동 {int(elapsed.total_seconds() // 60)}분)")

    if started or completed:
        print(f"  [작업지시] 시작 {started}건 / 완료 {completed}건")
# ─────────────────────────────────────────────────


# ── 센서 데이터 ────────────────────────────────────
def generate_data(equipment_id: str, rng: random.Random):
    is_fault = rng.random() < FAULT_RATE
    ranges = FAULT[equipment_id] if is_fault else NORMAL[equipment_id]
    payload = {
        "equipmentId": equipment_id,
        "temperature": round(rng.uniform(*ranges["temp"]), 2),
        "vibration":   round(rng.uniform(*ranges["vibration"]), 2),
        "rpm":         round(rng.uniform(*ranges["rpm"]), 2),
    }
    return payload, is_fault


def send_data(payload: dict) -> bool:
    try:
        resp = requests.post(
            f"{BASE_URL}/api/sensor/data",
            json=payload,
            timeout=5,
        )
        return resp.status_code == 200
    except requests.exceptions.RequestException as e:
        print(f"  [오류] 전송 실패: {e}")
        return False
# ─────────────────────────────────────────────────


def main():
    rng = random.Random(SEED)
    print(f"[시뮬레이터 시작] BASE_URL={BASE_URL}, INTERVAL={INTERVAL}s, "
          f"FAULT_RATE={FAULT_RATE*100:.1f}%, SEED={SEED}, "
          f"COMPLETE_AFTER={COMPLETE_AFTER_MIN}min")

    wait_for_backend()
    login()

    print("-" * 60)

    cycle = 0
    # 작업지시 관리 주기: 센서 10사이클마다 1회 (약 30초)
    WO_MANAGE_EVERY = 10

    while True:
        cycle += 1
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] Cycle #{cycle}")

        # ── 센서 데이터 전송
        for eq_id in EQUIPMENT_IDS:
            payload, is_fault = generate_data(eq_id, rng)
            ok = send_data(payload)
            send_icon  = "✅" if ok    else "❌"
            fault_tag  = " ⚠️ FAULT" if is_fault else ""
            print(f"  {send_icon} {eq_id} | "
                  f"온도={payload['temperature']}°C  "
                  f"진동={payload['vibration']}  "
                  f"RPM={payload['rpm']}"
                  f"{fault_tag}")

        # ── 작업지시 관리 (주기적)
        if cycle % WO_MANAGE_EVERY == 0:
            print(f"  [작업지시 점검]")
            manage_work_orders()

        time.sleep(INTERVAL)


if __name__ == "__main__":
    main()
