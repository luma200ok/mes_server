-- MES 운영 스키마 baseline (2026-08-20 mes_db 실덤프 기준)
--
-- 기존 운영 DB 는 flyway.baseline-on-migrate 로 이 버전이 "적용됨" 처리되며
-- 실제로 실행되지 않는다. 신규(로컬/테스트) DB 에서만 실행된다.
-- FK 제약명은 Hibernate 가 생성한 운영 실제 이름을 그대로 보존한다.

CREATE TABLE `equipment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `equipment_id` varchar(20) NOT NULL,
  `location` varchar(100) NOT NULL,
  `name` varchar(100) NOT NULL,
  `status` enum('FAULT','RUNNING','STOPPED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4fifs994jbukrs2w20a01cdqn` (`equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `equipment_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `max_rpm` double NOT NULL,
  `max_temperature` double NOT NULL,
  `max_vibration` double NOT NULL,
  `equipment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKh04cqyfyp5cvqjo5s2lphl93o` (`equipment_id`),
  CONSTRAINT `FKkpu6ssb9q5wiajqr3mxwylnai` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `work_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `completed_qty` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `defect_qty` int NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `good_qty` int NOT NULL,
  `planned_qty` int NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` enum('COMPLETED','DEFECTIVE','IN_PROGRESS','PENDING') NOT NULL,
  `work_order_no` varchar(30) NOT NULL,
  `equipment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgys4t64463658b7tchc785y94` (`work_order_no`),
  KEY `FKgsade1k8trkfpc3km5vwglxcp` (`equipment_id`),
  CONSTRAINT `FKgsade1k8trkfpc3km5vwglxcp` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `work_order_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `changed_at` datetime(6) NOT NULL,
  `changed_by` varchar(100) DEFAULT NULL,
  `from_status` enum('COMPLETED','DEFECTIVE','IN_PROGRESS','PENDING') NOT NULL,
  `to_status` enum('COMPLETED','DEFECTIVE','IN_PROGRESS','PENDING') NOT NULL,
  `work_order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqxdc4ix6tovtwyr0on2f59hh4` (`work_order_id`),
  CONSTRAINT `FKqxdc4ix6tovtwyr0on2f59hh4` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `defect` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `defect_type` enum('ASSEMBLY','DIMENSION','OTHER','RPM','SURFACE','TEMPERATURE','VIBRATION') NOT NULL,
  `detected_at` datetime(6) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `qty` int NOT NULL,
  `equipment_id` bigint NOT NULL,
  `work_order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqpmetx1lkh7vl1debq7bk2n8o` (`equipment_id`),
  KEY `FKew2p558twlk98y18qy3hbbgkh` (`work_order_id`),
  CONSTRAINT `FKew2p558twlk98y18qy3hbbgkh` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`id`),
  CONSTRAINT `FKqpmetx1lkh7vl1debq7bk2n8o` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sensor_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `avg_rpm` double NOT NULL,
  `avg_temperature` double NOT NULL,
  `avg_vibration` double NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `recorded_at` datetime(6) NOT NULL,
  `equipment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sensor_history_equipment` (`equipment_id`),
  KEY `idx_sensor_history_recorded_at` (`recorded_at`),
  CONSTRAINT `FKrpx8w5lmifro6fyn7eq8nbm65` FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 주의: equipment_id 가 FK 가 아닌 varchar(50) 이다 (다른 테이블과 불일치).
-- 후속 이슈에서 알람 라이프사이클 설계와 함께 정규화 검토.
CREATE TABLE `alarm_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `current_value` double NOT NULL,
  `discord_sent` bit(1) NOT NULL,
  `equipment_id` varchar(50) NOT NULL,
  `metric` varchar(20) NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `threshold` double NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_alarm_equipment_id` (`equipment_id`),
  KEY `idx_alarm_sent_at` (`sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('ADMIN','OPERATOR') NOT NULL,
  `username` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
