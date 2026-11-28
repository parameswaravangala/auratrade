package com.auratrade.selection;

import com.auratrade.strategy.StrategyRegistry;
import com.auratrade.strategy.builtin.MomentumBreakout;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The strategies stock selection runs. Add each code-based strategy here, once it exists; the
 * registry rejects duplicate or malformed ids at startup.
 */
@Configuration(proxyBeanMethods = false)
class SelectionConfig {

  @Bean
  StrategyRegistry strategyRegistry() {
    return new StrategyRegistry(List.of(new MomentumBreakout()));
  }
}
