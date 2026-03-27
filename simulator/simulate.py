"""
MES 센서 데이터 시뮬레이터
- 설비 3대 (EQ-001, EQ-002, EQ-003)
- 3초마다 POST /api/sensor/data
- 10% 확률로 임계값 초과 이상 데이터 발생
- RANDOM_SEED 환경변수로 재현 가능
"""

import os
import time
import random
import requests
from datetime import datetime

# ── 설정 ──────────────────────────────────────────
BASE_URL   = os.getenv("MES_BASE_URL", "http://localhost:8080")
INTERVAL   = int(os.getenv("SENSOR_INTERVAL", "3"))       # 초
FAULT_RATE = float(os.getenv("FAULT_RATE", "0.1"))        # 이상 데이터 비율
SEED       = int(os.getenv("RANDOM_SEED", "42"))

EQUIPMENT_IDS = ["EQ-001", "EQ-002", "EQ-003"]

# 정상 범위
NORMAL = {
    "EQ-001": {"temp": (60, 80),  "vibration": (1.0, 4.0),  "rpm": (1500, 2800)},
    "EQ-002": {"temp": (55, 75),  "vibration": (0.8, 3.8),  "rpm": (1400, 2600)},
    "EQ-003": {"temp": (50, 70),  "vibration": (0.5, 3.0),  "rpm": (1200, 2300)},
}

# 임계값 초과 범위 (이상 데이터)
FAULT = {
    "EQ-001": {"temp": (87, 100), "vibration": (5.5, 8.0),  "rpm": (3100, 3800)},
    "EQ-002": {"temp": (83, 95),  "vibration": (5.0, 7.5),  "rpm": (2900, 3500)},
    "EQ-003": {"temp": (78, 90),  "vibration": (4.0, 6.5),  "rpm": (2600, 3200)},
}
# ─────────────────────────────────────────────────


def generate_data(equipment_id: str, rng: random.Random) -> dict:
    is_fault = rng.random() < FAULT_RATE
    ranges = FAULT[equipment_id] if is_fault else NORMAL[equipment_id]

    return {
        "equipmentId": equipment_id,
        "temperature": round(rng.uniform(*ranges["temp"]), 2),
        "vibration":   round(rng.uniform(*ranges["vibration"]), 2),
        "rpm":         round(rng.uniform(*ranges["rpm"]), 2),
    }


def send_data(payload: dict) -> bool:
    try:
        resp = requests.post(
            f"{BASE_URL}/api/sensor/data",
            json=payload,
            timeout=5
        )
        return resp.status_code == 200
    except requests.exceptions.RequestException as e:
        print(f"  [오류] 전송 실패: {e}")
        return False


def main():
    rng = random.Random(SEED)
    print(f"[시뮬레이터 시작] BASE_URL={BASE_URL}, INTERVAL={INTERVAL}s, "
          f"FAULT_RATE={FAULT_RATE*100:.0f}%, SEED={SEED}")
    print("-" * 60)

    cycle = 0
    while True:
        cycle += 1
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] Cycle #{cycle}")

        for eq_id in EQUIPMENT_IDS:
            payload = generate_data(eq_id, rng)
            ok = send_data(payload)
            status = "✅" if ok else "❌"
            print(f"  {status} {eq_id} | 온도={payload['temperature']}°C  "
                  f"진동={payload['vibration']}  RPM={payload['rpm']}")

        time.sleep(INTERVAL)


if __name__ == "__main__":
    main()
