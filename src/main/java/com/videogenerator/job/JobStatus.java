package com.videogenerator.job;

/**
 * Job lifecycle state machine:
 * DRAFTING -> RENDERING -> PENDING_REVIEW -> APPROVED -> PUBLISHING -> PUBLISHED
 * DRAFTING/RENDERING -> FAILED, PENDING_REVIEW -> REJECTED
 */
public enum JobStatus {
    DRAFTING,
    RENDERING,
    PENDING_REVIEW,
    APPROVED,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    REJECTED
}
