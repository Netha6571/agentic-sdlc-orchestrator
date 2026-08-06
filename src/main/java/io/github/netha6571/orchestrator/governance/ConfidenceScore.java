package io.github.netha6571.orchestrator.governance;

import java.util.ArrayList;
import java.util.List;

/**
 * Confidence score built from observable run outcomes — never from
 * the model saying how sure it is about itself.
 *
 * Formula (from spec 04):
 *   Start at 0.
 *   +0.40  if the tests passed
 *   +0.25  if the code compiled
 *   +0.15  if the static checks were clean
 *   +0.20  base for finishing the run at all
 *   -0.10  for each fallback used
 *   -0.05  for each retry used
 *   Clamp to [0, 1].
 *
 * Signals are recorded alongside the score so the human sees
 * why the number is what it is, not just the number.
 */
public final class ConfidenceScore {

    private boolean testsPassed;
    private boolean codeCompiled;
    private boolean staticChecksClean;
    private boolean runFinished;
    private int fallbackCount;
    private int retryCount;

    public ConfidenceScore testsPassed(boolean v)       { this.testsPassed = v;       return this; }
    public ConfidenceScore codeCompiled(boolean v)      { this.codeCompiled = v;      return this; }
    public ConfidenceScore staticChecksClean(boolean v) { this.staticChecksClean = v; return this; }
    public ConfidenceScore runFinished(boolean v)       { this.runFinished = v;       return this; }
    public ConfidenceScore fallbackCount(int v)         { this.fallbackCount = v;     return this; }
    public ConfidenceScore retryCount(int v)            { this.retryCount = v;        return this; }

    /**
     * Compute the score. Every term maps to an observable outcome.
     */
    public double compute() {
        double score = 0.0;

        if (testsPassed)       score += 0.40;
        if (codeCompiled)      score += 0.25;
        if (staticChecksClean) score += 0.15;
        if (runFinished)       score += 0.20;

        score -= 0.10 * fallbackCount;
        score -= 0.05 * retryCount;

        // Clamp to [0, 1].
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * The signals that produced the score, so the human can
     * answer "why this number?" with facts, not a shrug.
     */
    public List<String> signals() {
        List<String> signals = new ArrayList<>();

        signals.add(signal("Tests passed",        testsPassed,       "+0.40"));
        signals.add(signal("Code compiled",       codeCompiled,      "+0.25"));
        signals.add(signal("Static checks clean", staticChecksClean, "+0.15"));
        signals.add(signal("Run finished",        runFinished,       "+0.20"));

        if (fallbackCount > 0) {
            signals.add(String.format("Fallbacks used: %d (-%.2f)",
                    fallbackCount, 0.10 * fallbackCount));
        }
        if (retryCount > 0) {
            signals.add(String.format("Retries used: %d (-%.2f)",
                    retryCount, 0.05 * retryCount));
        }

        return signals;
    }

    @Override
    public String toString() {
        double score = compute();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Confidence: %.2f\n", score));
        sb.append("Signals:\n");
        for (String s : signals()) {
            sb.append("  ").append(s).append("\n");
        }
        return sb.toString();
    }

    private static String signal(String name, boolean present, String weight) {
        return name + ": " + (present ? "yes (" + weight + ")" : "no");
    }
}
