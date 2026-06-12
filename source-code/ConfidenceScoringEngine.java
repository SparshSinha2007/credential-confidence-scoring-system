package engine;

import model.ConfidenceScore;
import model.LoginEvent;
import model.SessionBehavior;
import scoring.Scorer;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core engine for calculating confidence scores using the
 * Softmax Dynamic Scoring System.
 *
 * Instead of fixed weights, each scorer's contribution is dynamically
 * re-weighted at runtime using the Softmax function:
 *
 *   W_i  = e^(S_i) / Σ e^(S_i)          (Softmax weight for scorer i)
 *   Score = Σ (W_i · S_i) × 100          (weighted confidence score, 0-100)
 *
 * Scorers with higher raw scores automatically receive more weight,
 * making the system self-adapting — reliable, quality signals
 * contribute more to the final decision.
 *
 * Pure Java - no Spring, no repositories.
 */
public class ConfidenceScoringEngine {

    private final List<Scorer> scorers;

    public ConfidenceScoringEngine(List<Scorer> scorers) {
        this.scorers = scorers;
        System.out.println("ConfidenceScoringEngine initialized with Softmax Dynamic Scoring.");
        System.out.println("  Scorers registered (" + scorers.size() + "):");
        for (Scorer scorer : scorers) {
            System.out.println("    - " + scorer.getScorerName() + ": " + scorer.getDescription());
        }
    }

    /**
     * Calculates the confidence score for a session using Softmax dynamic weighting.
     *
     * @param loginEvent       the current login event
     * @param historicalLogins past login events for this user
     * @param behaviors        session behaviors
     * @return the calculated ConfidenceScore
     */
    public ConfidenceScore calculateScore(
            LoginEvent loginEvent,
            List<LoginEvent> historicalLogins,
            List<SessionBehavior> behaviors) {

        String sessionId = loginEvent.getSessionId();

        // ── Step 1: Collect raw scores from applicable scorers ─────────────────
        Map<String, Double> rawScores = new HashMap<>();
        for (Scorer scorer : scorers) {
            if (scorer.isApplicable(loginEvent, historicalLogins)) {
                double s = scorer.calculateScore(loginEvent, historicalLogins, behaviors);
                // Store score in [0, 100] range
                rawScores.put(scorer.getScorerName(), Math.max(0.0, Math.min(100.0, s)));
            }
        }

        if (rawScores.isEmpty()) {
            // No applicable scorers — return default score
            return buildResult(sessionId, Constants.DEFAULT_SCORE, rawScores);
        }

        // ── Step 2: Softmax weights ─────────────────────────────────────────────
        // W_i = e^(S_i) / Σ e^(S_i)
        // We normalise S_i to [0,1] before exponentiation to avoid numeric overflow.
        Map<String, Double> softmaxWeights = computeSoftmaxWeights(rawScores);

        // ── Step 3: Weighted confidence score ──────────────────────────────────
        // Confidence = Σ (W_i · S_i)   — S_i is already in [0,100]
        double overallScore = 0.0;
        for (Map.Entry<String, Double> entry : rawScores.entrySet()) {
            String name = entry.getKey();
            double si   = entry.getValue();
            double wi   = softmaxWeights.getOrDefault(name, 0.0);
            overallScore += wi * si;
        }

        overallScore = Math.max(Constants.MIN_SCORE, Math.min(Constants.MAX_SCORE, overallScore));

        return buildResult(sessionId, overallScore, rawScores);
    }

    /**
     * Computes Softmax weights for each scorer.
     * W_i = e^(si/100) / Σ e^(sj/100)
     * Dividing by 100 maps scores to [0,1] before exp(), preventing overflow.
     */
    private Map<String, Double> computeSoftmaxWeights(Map<String, Double> rawScores) {
        Map<String, Double> expValues = new HashMap<>();
        double sumExp = 0.0;

        for (Map.Entry<String, Double> entry : rawScores.entrySet()) {
            double expVal = Math.exp(entry.getValue() / 100.0); // e^(S_i/100)
            expValues.put(entry.getKey(), expVal);
            sumExp += expVal;
        }

        Map<String, Double> weights = new HashMap<>();
        for (Map.Entry<String, Double> entry : expValues.entrySet()) {
            weights.put(entry.getKey(), entry.getValue() / sumExp);
        }
        return weights;
    }

    /**
     * Builds the ConfidenceScore result object from raw scores and the overall score.
     */
    private ConfidenceScore buildResult(String sessionId, double overallScore,
                                         Map<String, Double> rawScores) {
        return ConfidenceScore.builder()
                .sessionId(sessionId)
                .overallScore(BigDecimal.valueOf(overallScore).setScale(2, RoundingMode.HALF_UP))
                .timeConsistencyScore(toBigDecimal(rawScores.get("TimeConsistency")))
                .locationConsistencyScore(toBigDecimal(rawScores.get("LocationConsistency")))
                .deviceStabilityScore(toBigDecimal(rawScores.get("DeviceStability")))
                .frequencyAnomalyScore(toBigDecimal(rawScores.get("FrequencyAnomaly")))
                .failedAttemptsScore(toBigDecimal(rawScores.get("FailedAttempts")))
                .scoreVersion(2) // version 2 = Softmax Dynamic Scoring
                .calculatedAt(Instant.now())
                .build();
    }

    /**
     * Returns the scorer weights configuration (nominal static weights).
     * Actual weights at runtime are computed dynamically by Softmax.
     */
    public Map<String, Double> getScorerWeights() {
        Map<String, Double> weights = new HashMap<>();
        for (Scorer scorer : scorers) {
            weights.put(scorer.getScorerName(), scorer.getWeight());
        }
        return weights;
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP) : null;
    }
}
