# ADR 0031: Java is the single source of production numbers; the Python lab reads exports only

- Status: proposed
- Date: 2026-11-27

## Context
The brief names Python for quant, backtesting and ML, and also says "avoid duplicating Java business logic in Python". Indicators, screens, backtests, the cost model and paper trading already exist in Java with tests. The Quant Head agreed that there should be one tested source of numbers and asked for a parity check. The Data Architect pointed out that writing Parquet from Java pulls in heavy dependencies (parquet-java with Hadoop; recalled, not verified).

## Decision
- Production indicators, backtests, sizing, costs and event price reactions stay in Java.
- `research/` is an optional Python 3.14 lab (JupyterLab, Polars, DuckDB). It never connects to Postgres.
- Export: a `research-export` job writes gzip CSV through PgJDBC `CopyManager`, which keeps exact NUMERIC text, with a JSON schema sidecar. It is incremental, one file per table per year. The lab converts to Parquet with DuckDB or Polars.
- A finding reaches production only as a Java implementation with tests and a `docs/research` note.
- Parity check: the lab recomputes one Java metric each night (for example SMA200 on NIFTY) from the export and fails loudly if they differ.

## Consequences
No duplicated logic and no Hadoop dependency. Research stays at least one day behind production. Revisit when we train a model whose outputs serve users. MLflow and model serving would then need their own ADR.
