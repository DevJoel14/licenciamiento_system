package com.zaga.licenciamientoapp.repository;

import com.zaga.licenciamientoapp.domain.PcInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PcInfoRepository extends JpaRepository<PcInfo, Long> {

    Optional<PcInfo> findByActivadorId(Long activadorId);

}
