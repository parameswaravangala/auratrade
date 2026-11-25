package com.auratrade;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Module boundaries as executable tests. Extend this as modules gain code (see the allowed
 * dependency table in the architecture docs): for example, only llmgateway may call LLM providers,
 * and agents must not depend on paperbroker.
 */
@AnalyzeClasses(packages = "com.auratrade", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule modulesAreFreeOfCycles =
      SlicesRuleDefinition.slices()
          .matching("com.auratrade.(*)..")
          .should()
          .beFreeOfCycles()
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule platformDoesNotDependOnFeatureModules =
      noClasses()
          .that()
          .resideInAPackage("com.auratrade.platform..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.auratrade.marketdata..",
              "com.auratrade.screener..",
              "com.auratrade.backtest..",
              "com.auratrade.news..",
              "com.auratrade.llmgateway..",
              "com.auratrade.agents..",
              "com.auratrade.playbook..",
              "com.auratrade.monitor..",
              "com.auratrade.risk..",
              "com.auratrade.paperbroker..",
              "com.auratrade.journal..",
              "com.auratrade.api..")
          .allowEmptyShould(true);
}
