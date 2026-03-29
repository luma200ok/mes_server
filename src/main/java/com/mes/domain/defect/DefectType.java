package com.mes.domain.defect;

public enum DefectType {
    DIMENSION, SURFACE, ASSEMBLY, OTHER,
    /** 센서 임계값 초과로 인한 자동 불량 */
    TEMPERATURE, VIBRATION, RPM
}
