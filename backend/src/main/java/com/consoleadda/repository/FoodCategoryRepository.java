package com.consoleadda.repository;

import com.consoleadda.entity.FoodCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodCategoryRepository extends JpaRepository<FoodCategory, Long> {

    List<FoodCategory> findAllByOrderByDisplayOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
