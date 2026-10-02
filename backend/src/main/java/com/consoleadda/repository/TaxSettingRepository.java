package com.consoleadda.repository;

import com.consoleadda.entity.TaxSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxSettingRepository extends JpaRepository<TaxSetting, Long> {
}
