package engine;

import model.ConfidenceScore;
import model.RiskDecision;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Classifies risk levels based on confidence scores.
 * Pure Java - hardcoded thresholds (LOW >= 80, MEDIUM >= 50, HIGH < 50).
 * Updated to recognise the 5th scorer: FailedAttempts.
 */
public class RiskClassifier {

    private final double lowRiskThreshold;
    private final double mediumRiskThreshold;

    public RiskClassifier() {
        this(80.0, 50.0);
    }

    public RiskClassifier(double lowRiskThreshold, double mediumRiskThreshold) {
        this.lowRiskThreshold = lowRiskThreshold;
        this.mediumRiskThreshold = mediumRiskThreshold;
    }

    /**
     * Classifies risk from a numeric score.
     */
    public RiskDecision.RiskLevel classifyRisk(double score) {
        if (score >= lowRiskThreshold)
            return RiskDecision.RiskLevel.LOW;
        else if (score >= mediumRiskThreshold)
            return RiskDecision.RiskLevel.MEDIUM;
        else
            return RiskDecision.RiskLevel.HIGH;
    }

    /**
     * Classifies risk from a ConfidenceScore entity.
     */
    public RiskDecision.RiskLevel classifyRisk(ConfidenceScore confidenceScore) {
        return classifyRisk(confidenceScore.getOverallScore().doubleValue());
    }

    /**
     * Generates a human-readable reason for the risk classification.
     */
    public String generateReason(ConfidenceScore confidenceScore, RiskDecision.RiskLevel riskLevel) {
        StringBuilder reason = new StringBuilder();

        switch (riskLevel) {
            case LOW -> reason.append("Session behavior is consistent with expected patterns. ");
            case MEDIUM -> reason.append("Some behavioral anomalies detected that warrant verification. ");
            case HIGH -> reason.append("Significant behavioral anomalies indicate potential unauthorized access. ");
        }

        appendLowScoreDetails(reason, confidenceScore);
        return reason.toString().trim();
    }

    /**
     * Identifies factors that contributed to a lower score.
     */
    public Map<String, Object> identifyContributingFactors(ConfidenceScore confidenceScore) {
        Map<String, Object> factors = new HashMap<>();
        BigDecimal score;

        score = confidenceScore.getTimeConsistencyScore();
        if (score != null && score.doubleValue() < 60) {
            factors.put("unusualTime", true);
            factors.put("timeScore", score.doubleValue());
        }

        score = confidenceScore.getLocationConsistencyScore();
        if (score != null && score.doubleValue() < 60) {
            factors.put("newLocation", true);
            factors.put("locationScore", score.doubleValue());
        }

        score = confidenceScore.getDeviceStabilityScore();
        if (score != null && score.doubleValue() < 60) {
            factors.put("newDevice", true);
            factors.put("deviceScore", score.doubleValue());
        }

        score = confidenceScore.getFrequencyAnomalyScore();
        if (score != null && score.doubleValue() < 60) {
            factors.put("highFrequency", true);
            factors.put("frequencyScore", score.doubleValue());
        }

        score = confidenceScore.getFailedAttemptsScore();
        if (score != null && score.doubleValue() < 60) {
            factors.put("failedAttempts", true);
            factors.put("failedAttemptsScore", score.doubleValue());
        }

        return factors;
    }

    private void appendLowScoreDetails(StringBuilder reason, ConfidenceScore confidenceScore) {
        BigDecimal score;

        score = confidenceScore.getTimeConsistencyScore();
        if (score != null && score.doubleValue() < 50) {
            reason.append("Login time is unusual for this user. ");
        }

        score = confidenceScore.getLocationConsistencyScore();
        if (score != null && score.doubleValue() < 50) {
            reason.append("Login location is inconsistent with history. ");
        }

        score = confidenceScore.getDeviceStabilityScore();
        if (score != null && score.doubleValue() < 50) {
            reason.append("Login from unrecognized device. ");
        }

        score = confidenceScore.getFrequencyAnomalyScore();
        if (score != null && score.doubleValue() < 50) {
            reason.append("Abnormal request patterns detected. ");
        }

        score = confidenceScore.getFailedAttemptsScore();
        if (score != null && score.doubleValue() < 50) {
            reason.append("Multiple failed login attempts detected. ");
        }
    }

    public Map<String, Double> getThresholds() {
        return Map.of("lowRisk", lowRiskThreshold, "mediumRisk", mediumRiskThreshold);
    }
}
