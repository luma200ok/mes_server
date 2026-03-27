package com.mes.domain.dashboard;

import com.mes.domain.dashboard.dto.QSensorStatisticsDto;
import com.mes.domain.dashboard.dto.QWorkOrderStatisticsDto;
import com.mes.domain.dashboard.dto.SensorStatisticsDto;
import com.mes.domain.dashboard.dto.WorkOrderStatisticsDto;
import com.mes.domain.defect.QDefect;
import com.mes.domain.workorder.QWorkOrder;
import com.mes.domain.workorder.WorkOrderStatus;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DashboardQueryRepositoryImpl implements DashboardQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<WorkOrderStatisticsDto> findWorkOrderStats(String equipmentId, LocalDate date) {
        QWorkOrder wo = QWorkOrder.workOrder;

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        WorkOrderStatisticsDto result = queryFactory
                .select(new QWorkOrderStatisticsDto(
                        wo.equipment.equipmentId,
                        wo.count(),
                        new CaseBuilder().when(wo.status.eq(WorkOrderStatus.COMPLETED)).then(1L).otherwise(0L).sum(),
                        new CaseBuilder().when(wo.status.eq(WorkOrderStatus.DEFECTIVE)).then(1L).otherwise(0L).sum(),
                        wo.plannedQty.sum(),
                        wo.completedQty.sum()
                ))
                .from(wo)
                .join(wo.equipment)
                .where(wo.equipment.equipmentId.eq(equipmentId)
                        .and(wo.createdAt.goe(start))
                        .and(wo.createdAt.lt(end)))
                .groupBy(wo.equipment.equipmentId)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<SensorStatisticsDto> findDefectStats(String equipmentId, LocalDate date) {
        QDefect d = QDefect.defect;

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        SensorStatisticsDto result = queryFactory
                .select(new QSensorStatisticsDto(
                        d.equipment.equipmentId,
                        d.qty.sum()
                ))
                .from(d)
                .join(d.equipment)
                .where(d.equipment.equipmentId.eq(equipmentId)
                        .and(d.detectedAt.goe(start))
                        .and(d.detectedAt.lt(end)))
                .groupBy(d.equipment.equipmentId)
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<WorkOrderStatisticsDto> findWorkOrderStatsByWeek(String equipmentId, LocalDate weekStart) {
        QWorkOrder wo = QWorkOrder.workOrder;

        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime end = weekStart.plusWeeks(1).atStartOfDay();

        return queryFactory
                .select(new QWorkOrderStatisticsDto(
                        wo.equipment.equipmentId,
                        wo.count(),
                        new CaseBuilder().when(wo.status.eq(WorkOrderStatus.COMPLETED)).then(1L).otherwise(0L).sum(),
                        new CaseBuilder().when(wo.status.eq(WorkOrderStatus.DEFECTIVE)).then(1L).otherwise(0L).sum(),
                        wo.plannedQty.sum(),
                        wo.completedQty.sum()
                ))
                .from(wo)
                .join(wo.equipment)
                .where(wo.equipment.equipmentId.eq(equipmentId)
                        .and(wo.createdAt.goe(start))
                        .and(wo.createdAt.lt(end)))
                .groupBy(wo.equipment.equipmentId)
                .fetch();
    }

    @Override
    public List<WorkOrderStatisticsDto> findWorkOrderStatsByMonth(String equipmentId, int year, int month) {
        QWorkOrder wo = QWorkOrder.workOrder;

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDateTime start = monthStart.atStartOfDay();
        LocalDateTime end = monthStart.plusMonths(1).atStartOfDay();

        return queryFactory
                .select(new QWorkOrderStatisticsDto(
                        wo.equipment.equipmentId,
                        wo.count(),
                        new CaseBuilder().when(wo.status.eq(WorkOrderStatus.COMPLETED)).then(1L).otherwise(0L).sum(),
                        new CaseBuilder().when(wo.status.eq(WorkOrderStatus.DEFECTIVE)).then(1L).otherwise(0L).sum(),
                        wo.plannedQty.sum(),
                        wo.completedQty.sum()
                ))
                .from(wo)
                .join(wo.equipment)
                .where(wo.equipment.equipmentId.eq(equipmentId)
                        .and(wo.createdAt.goe(start))
                        .and(wo.createdAt.lt(end)))
                .groupBy(wo.equipment.equipmentId)
                .fetch();
    }
}
