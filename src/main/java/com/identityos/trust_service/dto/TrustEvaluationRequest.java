package com.identityos.trust_service.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record TrustEvaluationRequest(
    @NotBlank String subjectType,
    @NotBlank String subjectId,
    String organizationId,
    String applicationId,
    String sessionId,
    Map<String, Object> context) {}
