package com.auratrade.strategy;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The code-based strategies the application knows, by id. Duplicate or malformed ids fail at
 * construction, so a misconfigured application does not start. Stored versions (plan slice 9) are
 * matched against this registry; a version whose strategy is missing fails closed.
 */
public final class StrategyRegistry {

  private final Map<String, Strategy> byId;

  public StrategyRegistry(Collection<? extends Strategy> strategies) {
    Map<String, Strategy> map = new LinkedHashMap<>();
    for (Strategy s : strategies) {
      if (!Strategy.ID.matcher(s.id()).matches()) {
        throw new IllegalArgumentException("malformed strategy id: " + s.id());
      }
      if (map.putIfAbsent(s.id(), s) != null) {
        throw new IllegalArgumentException("duplicate strategy id: " + s.id());
      }
    }
    this.byId = java.util.Collections.unmodifiableMap(map);
  }

  public Optional<Strategy> find(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  /**
   * @throws IllegalArgumentException if no strategy has this id
   */
  public Strategy get(String id) {
    return find(id).orElseThrow(() -> new IllegalArgumentException("unknown strategy: " + id));
  }

  public List<Strategy> all() {
    return List.copyOf(byId.values());
  }
}
