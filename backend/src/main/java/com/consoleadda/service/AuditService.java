package com.consoleadda.service;

import com.consoleadda.entity.AuditLog;
import com.consoleadda.repository.AuditLogRepository;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records important actions. Runs inside the caller's transaction, so an audit entry exists if and only if
 * the audited change was committed.
 */
@Slf4j
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Object entityId, Object oldValue, Object newValue) {
        AuditLog entry = new AuditLog();
        CurrentUser.get().ifPresent((AuthenticatedUser user) -> {
            entry.setUserId(user.id());
            entry.setUsername(user.username());
        });
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
        entry.setOldValue(toJson(oldValue));
        entry.setNewValue(toJson(newValue));
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            entry.setIpAddress(attrs.getRequest().getRemoteAddr());
        }
        repository.save(entry);
        log.info("AUDIT {} {} {} by {}", action, entityType, entityId, entry.getUsername());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Could not serialise audit value of type {}", value.getClass().getName(), e);
            return null;
        }
    }
}
