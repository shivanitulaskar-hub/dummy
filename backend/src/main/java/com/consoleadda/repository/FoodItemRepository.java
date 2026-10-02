package com.consoleadda.repository;

import com.consoleadda.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    @Query("select f from FoodItem f join fetch f.category c order by c.displayOrder, c.name, f.name")
    List<FoodItem> findAllOrdered();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByCategoryId(Long categoryId);

    /** Atomic stock change; always succeeds. Returns rows updated. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE food_items SET stock_quantity = stock_quantity + :delta WHERE id = :id", nativeQuery = true)
    int changeStock(@Param("id") Long id, @Param("delta") int delta);

    /** Atomic stock change that refuses to go below zero. Returns 0 when there isn't enough stock. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE food_items SET stock_quantity = stock_quantity + :delta "
            + "WHERE id = :id AND stock_quantity + :delta >= 0", nativeQuery = true)
    int changeStockIfAvailable(@Param("id") Long id, @Param("delta") int delta);
}
