package com.mes.global.scheduler;

import com.mes.domain.equipment.EquipmentRepository;
import com.mes.domain.workorder.WorkOrderService;
import com.mes.domain.workorder.dto.WorkOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderAutoCreateScheduler {

    private final EquipmentRepository equipmentRepository;
    private final WorkOrderService workOrderService;

    @Value("${mes.workorder.auto-create.planned-qty}")
    private int plannedQty;

    @Scheduled(cron = "${mes.workorder.auto-create.cron}")
    public void dailyRollover() {
        var equipments = equipmentRepository.findAll();
        log.info("[일일 작업지시 롤오버] 설비 {}개 / 계획수량 {}개/일", equipments.size(), plannedQty);

        int success = 0;
        for (var equipment : equipments) {
            try {
                workOrderService.rolloverDailyWorkOrder(equipment.getEquipmentId(), plannedQty);
                success++;
            } catch (Exception e) {
                log.error("[일일 작업지시 롤오버] 실패 equipmentId={}: {}", equipment.getEquipmentId(), e.getMessage());
            }
        }

        log.info("[일일 작업지시 롤오버] 완료 {}건", success);
    }
}
