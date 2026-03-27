package com.mes.domain.alarm;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AlarmHistoryRepository extends JpaRepository<AlarmHistory, Long> {

    Page<AlarmHistory> findByEquipmentIdOrderBySentAtDesc(String equipmentId, Pageable pageable);

    Page<AlarmHistory> findBySentAtBetweenOrderBySentAtDesc(LocalDateTime from, LocalDateTime to, Pageable pageable);

    long countByEquipmentIdAndSentAtAfter(String equipmentId, LocalDateTime since);
}
