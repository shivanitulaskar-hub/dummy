package com.consoleadda.repository;

import com.consoleadda.entity.GamingStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GamingStationRepository extends JpaRepository<GamingStation, Long> {

    List<GamingStation> findAllByOrderByDisplayOrderAscCodeAsc();

    List<GamingStation> findByActiveTrueOrderByDisplayOrderAscCodeAsc();
}
