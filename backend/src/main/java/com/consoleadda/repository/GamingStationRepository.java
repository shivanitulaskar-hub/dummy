package com.consoleadda.repository;

import com.consoleadda.entity.GamingStation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GamingStationRepository extends JpaRepository<GamingStation, Long> {

    List<GamingStation> findAllByOrderByDisplayOrderAscCodeAsc();

    List<GamingStation> findByActiveTrueOrderByDisplayOrderAscCodeAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    /** Row lock that serialises concurrent "start session" requests for the same station. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GamingStation s where s.id = :id")
    Optional<GamingStation> findByIdForUpdate(@Param("id") Long id);
}
