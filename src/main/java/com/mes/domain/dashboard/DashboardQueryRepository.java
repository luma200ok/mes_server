package com.mes.domain.dashboard;

import com.mes.domain.dashboard.dto.SensorStatisticsDto;
import com.mes.domain.dashboard.dto.WorkOrderStatisticsDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DashboardQueryRepository {

    Optional<WorkOrderStatisticsDto> findWorkOrderStats(String equipmentId, LocalDate date);

    Optional<SensorStatisticsDto> findDefectStats(String equipmentId, LocalDate date);

    List<WorkOrderStatisticsDto> findWorkOrderStatsByWeek(String equipmentId, LocalDate weekStart);

    List<WorkOrderStatisticsDto> findWorkOrderStatsByMonth(String equipmentId, int year, int month);
}
