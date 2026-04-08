"""
MES 센서 데이터 시뮬레이터
- 설비 목록 + 임계값을 서버 API에서 동적으로 로드
- 설비가 추가/변경되어도 시뮬레이터 코드 수정 불필요
- NORMAL 범위: 임계값의 70~90%
- FAULT  범위: 임계값의 100~120% (임계값 이상 즉시 불량)
- 3초마다 POST /api/sensor/data
- FAULT_RATE(기본 1%) 확률로 이상 데이터 발생
- PENDING 작업지시 자동 IN_PROGRESS 전환
- RANDOM_SEED 환경변수로 재현 가능
"""

import os
import time
import random
import requests
from datetime import datetime
from typing import Optional

# ── 설정 ──────────────────────────────────────────
BASE_URL   = os.getenv("MES_BASE_URL",        "http://localhost:8080")
INTERVAL   = int(os.getenv("SENSOR_INTERVAL", "3"))       # 센서 전송 주기 (초)
FAULT_RATE = float(os.getenv("FAULT_RATE",    "0.001"))    # 이상 데이터 비율 (0.1%)
SEED       = int(os.getenv("RANDOM_SEED",     "42"))
ADMIN_ID   = os.getenv("MES_ADMIN_ID",        "admin")
ADMIN_PW   = os.getenv("MES_ADMIN_PW",        "admin1234")

# 임계값 대비 정상/이상 범위 비율
NORMAL_RATIO = (0.70, 0.99)   # 임계값의 70~99%   → 정상
FAULT_RATIO  = (1.00, 1.20)   # 임계값의 100~120% → 이상 (임계값 초과 즉시 불량)
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
            print(f"[인증] 로그인 성공 ({ADMIN_ID})")
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
            requests.get(f"{BASE_URL}/api/auth/login", timeout=3)
            print(f"[대기] 백엔드 준비 완료 (시도 {attempt}회)")
            return
        except requests.exceptions.RequestException:
            print(f"  [{attempt}] 백엔드 미응답, {retry_interval}초 후 재시도...")
            attempt += 1
            time.sleep(retry_interval)
# ─────────────────────────────────────────────────


# ── 설비 설정 동적 로드 ───────────────────────────
def load_equipment_ranges():
    """
    서버 API에서 설비 목록 + 임계값을 로드하여 NORMAL/FAULT 범위를 동적 생성.
    설비가 추가되거나 임계값이 변경되면 재호출로 즉시 반영.

    반환:
        equipment_ids: 설비 ID 목록
        normal_ranges: {equipmentId: {temp, vibration, rpm}}
        fault_ranges:  {equipmentId: {temp, vibration, rpm}}
    """
    try:
        resp = requests.get(f"{BASE_URL}/api/equipment", headers=auth_headers(), timeout=5)
        if resp.status_code != 200:
            print(f"[설비 로드] 설비 목록 조회 실패 status={resp.status_code}")
            return None, None, None

        equipments = resp.json()
        equipment_ids = [eq["equipmentId"] for eq in equipments]

        normal_ranges = {}
        fault_ranges  = {}

        for eq in equipments:
            eq_id = eq["equipmentId"]
            cfg_resp = requests.get(
                f"{BASE_URL}/api/equipment-config/{eq_id}",
                headers=auth_headers(),
                timeout=5,
            )
            if cfg_resp.status_code != 200:
                print(f"  [설비 로드] {eq_id} 임계값 없음, 스킵")
                equipment_ids.remove(eq_id)
                continue

            cfg = cfg_resp.json()
            max_temp      = cfg["maxTemperature"]
            max_vibration = cfg["maxVibration"]
            max_rpm       = cfg["maxRpm"]

            normal_ranges[eq_id] = {
                "temp":      (max_temp      * NORMAL_RATIO[0], max_temp      * NORMAL_RATIO[1]),
                "vibration": (max_vibration * NORMAL_RATIO[0], max_vibration * NORMAL_RATIO[1]),
                "rpm":       (max_rpm       * NORMAL_RATIO[0], max_rpm       * NORMAL_RATIO[1]),
            }
            fault_ranges[eq_id] = {
                "temp":      (max_temp      * FAULT_RATIO[0], max_temp      * FAULT_RATIO[1]),
                "vibration": (max_vibration * FAULT_RATIO[0], max_vibration * FAULT_RATIO[1]),
                "rpm":       (max_rpm       * FAULT_RATIO[0], max_rpm       * FAULT_RATIO[1]),
            }
            print(f"  ✅ {eq_id} ({eq['name']}) — "
                  f"temp≤{max_temp} / vib≤{max_vibration} / rpm≤{max_rpm}")

        print(f"[설비 로드] {len(equipment_ids)}대 로드 완료")
        return equipment_ids, normal_ranges, fault_ranges

    except Exception as e:
        print(f"[설비 로드] 오류: {e}")
        return None, None, None
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


def change_work_order_status(wo_id: int, status: str) -> bool:
    """작업지시 상태 전이"""
    try:
        resp = requests.patch(
            f"{BASE_URL}/api/work-orders/{wo_id}/status",
            json={"status": status},
            headers=auth_headers(),
            timeout=5,
        )
        return resp.status_code == 200
    except Exception as e:
        print(f"[작업지시] 상태 변경 오류 id={wo_id}: {e}")
        return False


def manage_work_orders():
    """PENDING → IN_PROGRESS 자동 시작 (완료 처리는 서버가 자동 수행)"""
    orders = fetch_work_orders()
    if not orders:
        return

    started = 0
    for wo in orders:
        if wo.get("status") == "PENDING":
            eq_id = wo.get("equipmentId")
            if change_work_order_status(wo.get("id"), "IN_PROGRESS"):
                started += 1
                print(f"  ▶ [{eq_id}] {wo['workOrderNo']} PENDING → IN_PROGRESS")

    if started:
        print(f"  [작업지시] 시작 {started}건")
# ─────────────────────────────────────────────────


# ── 센서 데이터 ────────────────────────────────────
def generate_data(equipment_id: str, rng: random.Random, normal_ranges: dict, fault_ranges: dict):
    """
    센서 데이터 생성.
    - 정상: 3개 센서 모두 normal_range
    - 불량: 3개 중 1개만 fault_range, 나머지 2개는 normal_range
      (어떤 센서가 불량인지 랜덤 선택 → 불량 유형 분류에 활용)
    """
    is_fault   = rng.random() < FAULT_RATE
    normal     = normal_ranges[equipment_id]
    fault      = fault_ranges[equipment_id]
    fault_sensor = None

    if is_fault:
        fault_sensor = rng.choice(["temp", "vibration", "rpm"])

    def pick(sensor: str) -> float:
        r = fault[sensor] if (is_fault and sensor == fault_sensor) else normal[sensor]
        return round(rng.uniform(*r), 2)

    payload = {
        "equipmentId": equipment_id,
        "temperature": pick("temp"),
        "vibration":   pick("vibration"),
        "rpm":         pick("rpm"),
    }
    return payload, is_fault, fault_sensor


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
          f"FAULT_RATE={FAULT_RATE*100:.1f}%, SEED={SEED}")

    wait_for_backend()
    login()

    # 설비 설정 로드
    print("[설비 로드] 설비 목록 및 임계값 로드 중...")
    equipment_ids, normal_ranges, fault_ranges = load_equipment_ranges()
    if not equipment_ids:
        print("[오류] 설비 로드 실패. 시뮬레이터를 종료합니다.")
        return

    print("-" * 60)

    cycle = 0
    # 작업지시 관리 주기: 센서 10사이클마다 1회 (약 30초)
    WO_MANAGE_EVERY = 10
    # 설비 설정 재로드 주기: 100사이클마다 1회 (약 5분)
    # 임계값 변경 시 자동 반영
    CONFIG_RELOAD_EVERY = 100

    while True:
        cycle += 1
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] Cycle #{cycle}")

        # ── 센서 데이터 전송
        for eq_id in equipment_ids:
            payload, is_fault, fault_sensor = generate_data(eq_id, rng, normal_ranges, fault_ranges)
            ok        = send_data(payload)
            send_icon = "✅" if ok else "❌"
            fault_label_map = {"temp": "온도", "vibration": "진동", "rpm": "RPM"}
            fault_tag = f" ⚠️ FAULT({fault_label_map[fault_sensor]})" if is_fault else ""
            print(f"  {send_icon} {eq_id} | "
                  f"온도={payload['temperature']}°C  "
                  f"진동={payload['vibration']}  "
                  f"RPM={payload['rpm']}"
                  f"{fault_tag}")

        # ── 작업지시 관리 (주기적)
        if cycle % WO_MANAGE_EVERY == 0:
            print(f"  [작업지시 점검]")
            manage_work_orders()

        # ── 설비 설정 재로드 (임계값 변경 반영)
        if cycle % CONFIG_RELOAD_EVERY == 0:
            print("  [설비 재로드] 임계값 변경 확인 중...")
            new_ids, new_normal, new_fault = load_equipment_ranges()
            if new_ids:
                equipment_ids, normal_ranges, fault_ranges = new_ids, new_normal, new_fault

        time.sleep(INTERVAL)


if __name__ == "__main__":
    main()
