package com.identityos.trust_service.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.identityos.trust_service.dto.TrustEvaluationRequest;
import com.identityos.trust_service.dto.TrustScoreResponse;
import com.identityos.trust_service.dto.TrustSignalRequest;
import com.identityos.trust_service.repository.TrustSignalRepository;

@Service
public class TrustScoringService {
  private final TrustSignalRepository repository;

  public TrustScoringService(TrustSignalRepository repository) {
    this.repository = repository;
  }

  public TrustScoreResponse recordSignal(TrustSignalRequest request) {
    repository.saveSignal(request);
    return evaluate(
        new TrustEvaluationRequest(
            request.subjectType(),
            request.subjectId(),
            request.organizationId(),
            request.applicationId(),
            request.sessionId(),
            request.metadata()));
  }

  public TrustScoreResponse evaluate(TrustEvaluationRequest request) {
    List<Map<String, Object>> signals =
        repository.findSignals(request.subjectType(), request.subjectId(), request.applicationId(), 500);
    Score score = calculate(signals);
    double finalScore = clamp(score.identityScore + score.behaviourScore + score.riskPenalty, 0, 100);
    String level = trustLevel(finalScore);
    String decision = decision(finalScore);
    TrustScoreResponse response =
        new TrustScoreResponse(
            request.subjectType(),
            request.subjectId(),
            request.organizationId(),
            request.applicationId(),
            round(score.identityScore),
            round(score.behaviourScore),
            round(score.riskPenalty),
            round(finalScore),
            level,
            decision,
            score.reasons,
            repository.utcNow());
    repository.saveSnapshot(response);
    return response;
  }

  private Score calculate(List<Map<String, Object>> signals) {
    double identity = 20;
    double behaviour = 20;
    double risk = 0;
    int failedLogins = 0;
    int failedOtps = 0;
    int successfulLogins = 0;
    Set<LocalDate> successfulLoginDays = new HashSet<>();
    Set<String> appliedSignals = new HashSet<>();
    Set<String> reasons = new LinkedHashSet<>();

    for (Map<String, Object> signal : signals) {
      String type = string(signal.get("signal_type"));
      switch (type) {
        case "IDENTITY_REGISTERED" -> {
          if (appliedSignals.add("IDENTITY_REGISTERED")) {
            reasons.add("identity registered");
          }
        }
        case "EMAIL_VERIFIED" -> {
          if (appliedSignals.add("EMAIL_VERIFIED")) {
            identity += 3;
            reasons.add("email verified");
          }
        }
        case "MOBILE_VERIFIED", "PHONE_VERIFIED" -> {
          if (appliedSignals.add("MOBILE_VERIFIED")) {
            identity += 5;
            reasons.add("mobile verified");
          }
        }
        case "AADHAAR_VERIFIED", "AADHAR_VERIFIED" -> {
          if (appliedSignals.add("AADHAAR_VERIFIED")) {
            identity += 10;
            reasons.add("aadhaar verified");
          }
        }
        case "PAN_VERIFIED", "GOVERNMENT_ID_VERIFIED" -> {
          if (appliedSignals.add(type)) {
            identity += 10;
            reasons.add(type.toLowerCase(Locale.ROOT).replace('_', ' '));
          }
        }
        case "PROFILE_COMPLETED" -> {
          if (appliedSignals.add("PROFILE_COMPLETED")) {
            identity += 2;
            reasons.add("profile completed");
          }
        }
        case "MFA_ENABLED" -> {
          if (appliedSignals.add("MFA_ENABLED")) {
            identity += 10;
            reasons.add("mfa enabled");
          }
        }
        case "STRONG_AUTH_METHOD" -> {
          if (appliedSignals.add("STRONG_AUTH_METHOD")) {
            identity += 8;
            reasons.add("strong authentication method");
          }
        }
        case "LOGIN_SUCCESS" -> {
          successfulLogins++;
          LocalDate loginDate = occurredDate(signal.get("occurred_at"));
          if (loginDate != null) {
            successfulLoginDays.add(loginDate);
          }
          if (appliedSignals.add("LOGIN_SUCCESS")) {
            behaviour += 5;
            reasons.add("login success");
          }
        }
        case "REGULAR_LOGIN_PATTERN", "CONSISTENT_LOGIN_BEHAVIOUR" -> {
          if (appliedSignals.add("REGULAR_LOGIN_PATTERN")) {
            behaviour += 5;
            reasons.add("regular login pattern");
          }
        }
        case "OTP_VERIFIED" -> {
          if (appliedSignals.add("OTP_VERIFIED")) {
            behaviour += 5;
            reasons.add("otp verified");
          }
        }
        case "KNOWN_DEVICE_LOGIN" -> {
          if (appliedSignals.add("KNOWN_DEVICE_LOGIN")) {
            behaviour += 10;
            reasons.add("known device");
          }
        }
        case "KNOWN_LOCATION_LOGIN" -> {
          if (appliedSignals.add("KNOWN_LOCATION_LOGIN")) {
            behaviour += 8;
            reasons.add("known location");
          }
        }
        case "LOGIN_FAILED" -> failedLogins++;
        case "OTP_FAILED" -> failedOtps++;
        case "NEW_DEVICE_LOGIN" -> {
          risk -= 5;
          reasons.add("new device");
        }
        case "SUSPICIOUS_IP_DETECTED" -> {
          risk -= 10;
          reasons.add("suspicious ip");
        }
        case "IMPOSSIBLE_TRAVEL" -> {
          risk -= 15;
          reasons.add("impossible travel");
        }
        case "BOT_DETECTED" -> {
          risk -= 15;
          reasons.add("bot detection");
        }
        default -> {
          Double customWeight = number(signal.get("weight"));
          if (customWeight != null && appliedSignals.add(type)) {
            behaviour += customWeight;
            reasons.add(type.toLowerCase(Locale.ROOT).replace('_', ' '));
          }
        }
      }
    }

    if (failedLogins > 0) {
      double penalty = failedLogins >= 3 ? -10 : -3.0 * failedLogins;
      risk += penalty;
      reasons.add(failedLogins + " failed login attempt(s)");
    }
    if (failedOtps > 0) {
      double penalty = failedOtps >= 3 ? -8 : -2.0 * failedOtps;
      risk += penalty;
      reasons.add(failedOtps + " failed otp attempt(s)");
    }
    if (successfulLoginDays.size() >= 3 && successfulLogins >= 3 && appliedSignals.add("CONSISTENT_LOGIN_HISTORY")) {
      behaviour += 5;
      reasons.add("consistent login history");
    } else if (successfulLogins >= 5 && appliedSignals.add("REPEATED_SUCCESSFUL_LOGIN")) {
      behaviour += 2;
      reasons.add("repeated successful login");
    }

    return new Score(
        clamp(identity, 0, 70), clamp(behaviour, 0, 30), clamp(risk, -40, 0), List.copyOf(reasons));
  }

  private String trustLevel(double score) {
    if (score >= 80) return "HIGH";
    if (score >= 60) return "MEDIUM";
    if (score >= 40) return "LOW";
    return "CRITICAL";
  }

  private String decision(double score) {
    if (score >= 80) return "ALLOW";
    if (score >= 60) return "ALLOW_WITH_MONITORING";
    if (score >= 40) return "STEP_UP_AUTHENTICATION";
    if (score >= 20) return "MANUAL_REVIEW";
    return "DENY";
  }

  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private String string(Object value) {
    return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
  }

  private LocalDate occurredDate(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.toLocalDate();
    }
    if (value instanceof java.sql.Date date) {
      return date.toLocalDate();
    }
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }
    try {
      return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T')).toLocalDate();
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private Double number(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private record Score(
      double identityScore, double behaviourScore, double riskPenalty, List<String> reasons) {}
}
