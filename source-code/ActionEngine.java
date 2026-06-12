package engine;

import model.ConfidenceScore;
import model.RiskDecision;

import java.time.Instant;
import java.util.Map;

/**
 * Determines and records actions based on risk levels.
 * Pure Java - no repositories, just builds and returns RiskDecision.
 */
public class ActionEngine {

    private final RiskClassifier riskClassifier;

    public ActionEngine(RiskClassifier riskClassifier) {
        this.riskClassifier = riskClassifier;
    }

    /**
     * Determines the action to take based on a confidence score.
     *
     * @param confidenceScore the calculated confidence score
     * @return the RiskDecision with action details
     */
    public RiskDecision determineAction(ConfidenceScore confidenceScore) {
        // Classify risk level
        RiskDecision.RiskLevel riskLevel = riskClassifier.classifyRisk(confidenceScore);

        // Determine action
        RiskDecision.ActionType action = determineActionType(riskLevel);

        // Generate reason
        String reason = riskClassifier.generateReason(confidenceScore, riskLevel);

        // Identify contributing factors
        Map<String, Object> factors = riskClassifier.identifyContributingFactors(confidenceScore);

        // Build the decision
        RiskDecision decision = RiskDecision.builder()
                .sessionId(confidenceScore.getSessionId())
                .confidenceScore(confidenceScore)
                .riskLevel(riskLevel)
                .action(action)
                .reason(reason)
                .additionalFactors(factors)
                .requiresStepUp(action == RiskDecision.ActionType.STEP_UP_VERIFICATION)
                .incidentLogged(action == RiskDecision.ActionType.BLOCK)
                .createdAt(Instant.now())
                .build();

        // Print incident if HIGH risk
        if (decision.getIncidentLogged()) {
            System.out.println("  ⚠ SECURITY INCIDENT: Session " + decision.getSessionId() +
                    " blocked. Risk: " + decision.getRiskLevel() +
                    ", Score: " + confidenceScore.getOverallScore() +
                    ", Reason: " + decision.getReason());
        }

        return decision;
    }

    private RiskDecision.ActionType determineActionType(RiskDecision.RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> RiskDecision.ActionType.ALLOW;
            case MEDIUM -> RiskDecision.ActionType.STEP_UP_VERIFICATION;
            case HIGH -> RiskDecision.ActionType.BLOCK;
        };
    }
}
