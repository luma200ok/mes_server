package com.mes.domain.sensor;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorData implements Serializable {

    private String equipmentId;
    private Double temperature;
    private Double vibration;
    private Double rpm;
    private String status;
    private LocalDateTime timestamp;
}
