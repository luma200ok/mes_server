package com.mes.domain.defect;

import com.mes.domain.defect.dto.DefectTypeStatDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface DefectQueryRepository {

    Page<Defect> findFiltered(LocalDate startDate, LocalDate endDate,
                              DefectType defectType, String equipmentId,
                              Pageable pageable);

    List<DefectTypeStatDto> aggregateByType(LocalDate startDate, LocalDate endDate, String equipmentId);
}
