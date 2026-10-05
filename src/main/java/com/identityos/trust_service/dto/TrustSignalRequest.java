package com.identityos.trust_service.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.Map;

public record TrustSignalRequest(
    @NotBlank String subjectType,
    @NotBlank String subjectId,
    String organizationId,
    String applicationId,
    String sessionId,
    @NotBlank String signalType,
    String outcome,
    Double weight,
    String source,
    Map<String, Object> metadata,
    OffsetDateTime occurredAt) {}
