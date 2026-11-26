package com.auratrade.marketdata;

import java.time.Instant;

public record QualityIssue(Severity severity, String code, Instant ts, String message) {}
