package com.identityos.trust_service.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TrustScoreResponse(
    String subjectType,
    String subjectId,
    String organizationId,
    String applicationId,
    double identityScore,
    double behaviourScore,
    double riskPenalty,
    double finalScore,
    String trustLevel,
    String decision,
    List<String> reasons,
    OffsetDateTime calculatedAt) {}
