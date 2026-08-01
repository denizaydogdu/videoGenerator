package com.videogenerator.job;

/**
 * Hard monthly spending cap. Checked BEFORE any paid API call so a retry
 * loop or misconfiguration cannot run up a bill overnight.
 *
 * Known limitation (accepted): check and spend are not atomic across
 * processes. This deployment has a single spending process (the pipeline,
 * serialized by the scheduler); the backoffice only reads. If multiple
 * concurrent spender processes are ever introduced, move the check into
 * CostTracker under its FileLock (check-and-reserve).
 */
public class BudgetGuard {
    private final CostTracker tracker;
    private final double monthlyLimitUsd;

    public BudgetGuard(CostTracker tracker, double monthlyLimitUsd) {
        this.tracker = tracker;
        this.monthlyLimitUsd = monthlyLimitUsd;
    }

    /**
     * @throws IllegalStateException if spent + estimate would exceed the cap
     */
    public void assertAllows(double estimatedUsd) {
        double spent = tracker.spentThisMonth();
        if (spent + estimatedUsd > monthlyLimitUsd) {
            throw new IllegalStateException(String.format(
                    "Budget exceeded: spent=%.2f + estimate=%.2f > limit=%.2f",
                    spent, estimatedUsd, monthlyLimitUsd));
        }
    }
}
