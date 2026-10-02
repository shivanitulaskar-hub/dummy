package com.consoleadda.repository;

import com.consoleadda.entity.GameMaster;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameMasterRepository extends JpaRepository<GameMaster, Long> {

    List<GameMaster> findAllByOrderByNameAscCopyNumberAsc();

    boolean existsByNameIgnoreCaseAndCopyNumber(String name, int copyNumber);

    boolean existsByNameIgnoreCaseAndCopyNumberAndIdNot(String name, int copyNumber, Long id);

    /** Row lock that serialises concurrent "rent out" requests for the same disc. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameMaster g where g.id = :id")
    Optional<GameMaster> findByIdForUpdate(@Param("id") Long id);
}
