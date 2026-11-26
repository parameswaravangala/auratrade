package com.auratrade.marketdata;

import java.util.List;

public record QualityReport(List<QualityIssue> issues) {

  public QualityReport {
    issues = List.copyOf(issues);
  }

  public boolean hasErrors() {
    return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
  }

  public boolean isClean() {
    return issues.isEmpty();
  }

  public long count(String code) {
    return issues.stream().filter(i -> i.code().equals(code)).count();
  }
}
