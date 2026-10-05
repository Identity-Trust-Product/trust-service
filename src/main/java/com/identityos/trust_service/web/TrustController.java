package com.identityos.trust_service.web;

import com.identityos.trust_service.dto.TrustEvaluationRequest;
import com.identityos.trust_service.dto.TrustScoreResponse;
import com.identityos.trust_service.dto.TrustSignalRequest;
import com.identityos.trust_service.repository.TrustSignalRepository;
import com.identityos.trust_service.service.TrustScoringService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trust")
public class TrustController {
  private final TrustScoringService trustScoringService;
  private final TrustSignalRepository trustSignalRepository;

  public TrustController(
      TrustScoringService trustScoringService, TrustSignalRepository trustSignalRepository) {
    this.trustScoringService = trustScoringService;
    this.trustSignalRepository = trustSignalRepository;
  }

  @PostMapping("/signals")
  public ResponseEntity<TrustScoreResponse> recordSignal(@Valid @RequestBody TrustSignalRequest request) {
    return ResponseEntity.accepted().body(trustScoringService.recordSignal(request));
  }

  @PostMapping("/evaluate")
  public ResponseEntity<TrustScoreResponse> evaluate(@Valid @RequestBody TrustEvaluationRequest request) {
    return ResponseEntity.ok(trustScoringService.evaluate(request));
  }

  @GetMapping("/users/{userId}")
  public ResponseEntity<TrustScoreResponse> userScore(
      @PathVariable String userId,
      @RequestParam(required = false) String organizationId,
      @RequestParam(required = false) String applicationId) {
    return ResponseEntity.ok(
        trustScoringService.evaluate(
            new TrustEvaluationRequest("USER", userId, organizationId, applicationId, null, Map.of())));
  }

  @GetMapping("/applications/{applicationId}/users/{userId}")
  public ResponseEntity<TrustScoreResponse> applicationUserScore(
      @PathVariable String applicationId, @PathVariable String userId) {
    return ResponseEntity.ok(
        trustScoringService.evaluate(
            new TrustEvaluationRequest("USER", userId, null, applicationId, null, Map.of())));
  }

  @GetMapping("/events")
  public ResponseEntity<?> events(
      @RequestParam(required = false) String subjectId, @RequestParam(defaultValue = "100") int limit) {
    return ResponseEntity.ok(
        trustSignalRepository.recentEvents(subjectId, Math.max(1, Math.min(limit, 500))));
  }

  @GetMapping("/decisions")
  public ResponseEntity<?> decisions(
      @RequestParam(required = false) String subjectId, @RequestParam(defaultValue = "100") int limit) {
    return ResponseEntity.ok(
        trustSignalRepository.recentDecisions(subjectId, Math.max(1, Math.min(limit, 500))));
  }
}
