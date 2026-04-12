package com.mes.domain.workorder;

import com.mes.domain.defect.DefectRepository;
import com.mes.domain.equipment.Equipment;
import com.mes.domain.equipment.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderRolloverTest {

    @Mock WorkOrderRepository workOrderRepository;
    @Mock WorkOrderHistoryRepository historyRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock DefectRepository defectRepository;
    @Mock RedisTemplate<String, Object> redisTemplate;

    @InjectMocks WorkOrderService workOrderService;

    private Equipment equipment;

    @BeforeEach
    void setUp() {
        equipment = Equipment.builder()
                .equipmentId("EQ-001")
                .name("CNC 1호")
                .location("A동")
                .build();

        given(workOrderRepository.countByWorkOrderNoStartingWith(any())).willReturn(0L);
        workOrderService.initSeq();
    }

    @Test
    @DisplayName("IN_PROGRESS WO → 자정 롤오버 시 COMPLETED 처리 후 새 WO 생성")
    void rollover_inProgress_completesAndCreatesNew() {
        // given
        WorkOrder inProgressWo = WorkOrder.builder()
                .workOrderNo("WO-20260412-001")
                .equipment(equipment)
                .plannedQty(1000)
                .build();
        inProgressWo.transitionTo(WorkOrderStatus.IN_PROGRESS, null); // PENDING → IN_PROGRESS
        inProgressWo.addGoodQty(750);
        inProgressWo.addDefectQty(30);

        given(workOrderRepository.findFirstByEquipment_EquipmentIdAndStatus("EQ-001", WorkOrderStatus.IN_PROGRESS))
                .willReturn(Optional.of(inProgressWo));
        given(workOrderRepository.findFirstByEquipment_EquipmentIdAndStatus("EQ-001", WorkOrderStatus.PENDING))
                .willReturn(Optional.empty());
        given(equipmentRepository.findByEquipmentId("EQ-001"))
                .willReturn(Optional.of(equipment));

        // when
        workOrderService.rolloverDailyWorkOrder("EQ-001", 1000);

        // then
        assertThat(inProgressWo.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(inProgressWo.getCompletedQty()).isEqualTo(750); // goodQty 기준

        // 이력 저장 확인
        ArgumentCaptor<WorkOrderHistory> historyCaptor = ArgumentCaptor.forClass(WorkOrderHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo("SYSTEM_DAILY_ROLLOVER");

        // Redis active 키 삭제 확인
        verify(redisTemplate).delete("wo:active:EQ-001");

        // 새 WO 생성 확인
        ArgumentCaptor<WorkOrder> newWoCaptor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(newWoCaptor.capture());
        assertThat(newWoCaptor.getValue().getPlannedQty()).isEqualTo(1000);
        assertThat(newWoCaptor.getValue().getStatus()).isEqualTo(WorkOrderStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING WO → 자정 롤오버 시 삭제 후 새 WO 생성")
    void rollover_pending_deletesAndCreatesNew() {
        // given
        WorkOrder pendingWo = WorkOrder.builder()
                .workOrderNo("WO-20260412-001")
                .equipment(equipment)
                .plannedQty(1000)
                .build();

        given(workOrderRepository.findFirstByEquipment_EquipmentIdAndStatus("EQ-001", WorkOrderStatus.IN_PROGRESS))
                .willReturn(Optional.empty());
        given(workOrderRepository.findFirstByEquipment_EquipmentIdAndStatus("EQ-001", WorkOrderStatus.PENDING))
                .willReturn(Optional.of(pendingWo));
        given(equipmentRepository.findByEquipmentId("EQ-001"))
                .willReturn(Optional.of(equipment));

        // when
        workOrderService.rolloverDailyWorkOrder("EQ-001", 1000);

        // then
        verify(workOrderRepository).delete(pendingWo);
        verify(historyRepository, never()).save(any());
        verify(redisTemplate, never()).delete(any(String.class));

        ArgumentCaptor<WorkOrder> newWoCaptor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(newWoCaptor.capture());
        assertThat(newWoCaptor.getValue().getPlannedQty()).isEqualTo(1000);
    }

    @Test
    @DisplayName("활성 WO 없음 → 자정 롤오버 시 새 WO만 생성")
    void rollover_noActiveWo_createsNew() {
        // given
        given(workOrderRepository.findFirstByEquipment_EquipmentIdAndStatus("EQ-001", WorkOrderStatus.IN_PROGRESS))
                .willReturn(Optional.empty());
        given(workOrderRepository.findFirstByEquipment_EquipmentIdAndStatus("EQ-001", WorkOrderStatus.PENDING))
                .willReturn(Optional.empty());
        given(equipmentRepository.findByEquipmentId("EQ-001"))
                .willReturn(Optional.of(equipment));

        // when
        workOrderService.rolloverDailyWorkOrder("EQ-001", 1000);

        // then
        verify(historyRepository, never()).save(any());
        verify(workOrderRepository, never()).delete(any(WorkOrder.class));
        verify(redisTemplate, never()).delete(any(String.class));

        ArgumentCaptor<WorkOrder> newWoCaptor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(newWoCaptor.capture());
        assertThat(newWoCaptor.getValue().getPlannedQty()).isEqualTo(1000);
    }
}
