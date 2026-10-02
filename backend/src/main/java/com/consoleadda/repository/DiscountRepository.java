package com.consoleadda.repository;

import com.consoleadda.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    List<Discount> findAllByOrderByActiveDescNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
