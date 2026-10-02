package com.consoleadda.repository;

import com.consoleadda.entity.InventoryTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @Query("""
            select t from InventoryTransaction t join fetch t.foodItem f
            where (:itemId is null or f.id = :itemId)
              and t.businessDate between :from and :to
            order by t.id desc""")
    List<InventoryTransaction> search(@Param("itemId") Long itemId, @Param("from") LocalDate from,
                                      @Param("to") LocalDate to, Pageable pageable);

    @Query("select coalesce(sum(t.quantity), 0) from InventoryTransaction t where t.foodItem.id = :itemId")
    long ledgerBalance(@Param("itemId") Long itemId);
}
