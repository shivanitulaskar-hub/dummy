package com.consoleadda.repository;

import com.consoleadda.entity.PricingPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PricingPlanRepository extends JpaRepository<PricingPlan, Long> {

    @EntityGraph(attributePaths = "rules")
    List<PricingPlan> findAllByOrderByActiveDescPriorityDescNameAsc();

    @EntityGraph(attributePaths = "rules")
    List<PricingPlan> findByActiveTrue();

    @EntityGraph(attributePaths = "rules")
    Optional<PricingPlan> findWithRulesById(Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
