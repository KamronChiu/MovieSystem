package com.eduaccess.exception;

// ── SESSION 12: Exception hierarchy using inheritance ─────────

/** Base for all EduAccess domain exceptions. */
public abstract class EduAccessException extends RuntimeException {
    public EduAccessException(String message) { super(message); }
}
