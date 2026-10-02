package com.consoleadda.service;

import com.consoleadda.entity.AppSetting;
import com.consoleadda.repository.AppSettingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Optional;

/** Typed read access to business settings. A missing or malformed value falls back to the default and is logged. */
@Slf4j
@Service
@Transactional(readOnly = true)
public class SettingsService {

    private final AppSettingRepository repository;

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    public String getString(String key, String defaultValue) {
        return raw(key).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return raw(key).map(v -> {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                log.error("Setting {} has invalid integer value '{}'; using {}", key, v, defaultValue);
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return raw(key).map(v -> Boolean.parseBoolean(v.trim())).orElse(defaultValue);
    }

    public LocalTime getTime(String key, LocalTime defaultValue) {
        return raw(key).map(v -> {
            try {
                return LocalTime.parse(v.trim());
            } catch (RuntimeException e) {
                log.error("Setting {} has invalid time value '{}'; using {}", key, v, defaultValue);
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    private Optional<String> raw(String key) {
        Optional<String> value = repository.findById(key).map(AppSetting::getValue).filter(v -> !v.isBlank());
        if (value.isEmpty()) {
            log.warn("Setting {} is missing; using default", key);
        }
        return value;
    }
}
