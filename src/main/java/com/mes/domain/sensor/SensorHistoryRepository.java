package com.mes.domain.sensor;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface SensorHistoryRepository extends JpaRepository<SensorHistory, Long> {

    @EntityGraph(attributePaths = "equipment")
    List<SensorHistory> findByEquipment_EquipmentIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            String equipmentId, LocalDateTime from, LocalDateTime to);

    @Modifying
    @Query("DELETE FROM SensorHistory s WHERE s.deletedAt IS NOT NULL AND s.deletedAt < :cutoff")
    int hardDeleteBefore(LocalDateTime cutoff);

    @Modifying
    @Query("UPDATE SensorHistory s SET s.deletedAt = :now WHERE s.equipment.id IN " +
           "(SELECT e.id FROM Equipment e WHERE e.deletedAt IS NOT NULL) AND s.deletedAt IS NULL")
    int softDeleteOrphanedByEquipment(LocalDateTime now);
}
