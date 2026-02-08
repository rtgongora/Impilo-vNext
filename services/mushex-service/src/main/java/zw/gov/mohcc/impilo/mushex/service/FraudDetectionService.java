package zw.gov.mohcc.impilo.mushex.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service for detecting and flagging potentially fraudulent payment activity.
 */
public interface FraudDetectionService {

    /**
     * Retrieve fraud flags for a tenant, paginated.
     */
    Page<Object> getFlags(UUID tenantId, Pageable pageable);
}
