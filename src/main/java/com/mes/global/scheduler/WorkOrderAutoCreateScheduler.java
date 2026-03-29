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
    public void autoCreate() {
        var equipments = equipmentRepository.findAll();
        log.info("[작업지시 자동 생성] 설비 {}개 × {}개 = 총 {}건 생성 시작",
                equipments.size(), plannedQty, equipments.size() * plannedQty);

        int success = 0, skipped = 0;
        for (var equipment : equipments) {
            try {
                // 이미 활성(PENDING/IN_PROGRESS) 작업지시가 있으면 생성 스킵
                if (workOrderService.hasActiveWorkOrder(equipment.getEquipmentId())) {
                    skipped++;
                    continue;
                }
                workOrderService.create(new WorkOrderRequest(equipment.getEquipmentId(), plannedQty));
                success++;
            } catch (Exception e) {
                log.error("[작업지시 자동 생성] 실패 equipmentId={}: {}", equipment.getEquipmentId(), e.getMessage());
            }
        }

        log.info("[작업지시 자동 생성] 생성 {}건 / 스킵 {}건", success, skipped);
    }
}
