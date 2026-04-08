package com.mes.domain.defect;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DefectRepository extends JpaRepository<Defect, Long>, DefectQueryRepository {

    @EntityGraph(attributePaths = {"workOrder", "workOrder.equipment", "equipment"})
    List<Defect> findByWorkOrder_Id(Long workOrderId);

    @Query("SELECT COALESCE(SUM(d.qty), 0) FROM Defect d WHERE d.workOrder.id = :workOrderId")
    int sumQtyByWorkOrderId(Long workOrderId);
}
