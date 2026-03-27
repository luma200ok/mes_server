package com.mes.global.init;

import com.mes.domain.equipment.Equipment;
import com.mes.domain.equipment.EquipmentConfig;
import com.mes.domain.equipment.EquipmentConfigRepository;
import com.mes.domain.equipment.EquipmentRepository;
import com.mes.domain.equipment.EquipmentStatus;
import com.mes.domain.user.User;
import com.mes.domain.user.UserRepository;
import com.mes.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentConfigRepository equipmentConfigRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initAdminUser();
        initEquipments();
    }

    private void initAdminUser() {
        if (userRepository.existsByUsername("admin")) {
            return;
        }
        userRepository.save(User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin1234"))
                .role(UserRole.ADMIN)
                .build());
        log.info("관리자 계정 초기화 완료 (admin/admin1234)");
    }

    private void initEquipments() {
        List<EquipmentSeed> seeds = List.of(
                new EquipmentSeed("EQ-001", "CNC 가공기 1호", "A동 1층", 85.0, 5.0, 3000.0),
                new EquipmentSeed("EQ-002", "CNC 가공기 2호", "A동 2층", 80.0, 4.5, 2800.0),
                new EquipmentSeed("EQ-003", "조립 로봇",      "B동 1층", 75.0, 3.5, 2500.0)
        );

        for (EquipmentSeed seed : seeds) {
            if (equipmentRepository.existsByEquipmentId(seed.equipmentId())) {
                continue;
            }
            Equipment equipment = Equipment.builder()
                    .equipmentId(seed.equipmentId())
                    .name(seed.name())
                    .location(seed.location())
                    .status(EquipmentStatus.STOPPED)
                    .build();
            equipmentRepository.save(equipment);

            EquipmentConfig config = EquipmentConfig.builder()
                    .equipment(equipment)
                    .maxTemperature(seed.maxTemp())
                    .maxVibration(seed.maxVibration())
                    .maxRpm(seed.maxRpm())
                    .build();
            equipmentConfigRepository.save(config);
            log.info("설비 초기화 완료: {} ({})", seed.equipmentId(), seed.name());
        }
    }

    private record EquipmentSeed(
            String equipmentId, String name, String location,
            double maxTemp, double maxVibration, double maxRpm) {}
}
