-- ADR 0017: scheduled jobs. Run history drives catch-up (what is still owed), and a lease row
-- stops the same job running twice at once, across threads and instances.

CREATE TABLE job_run (
  id            BIGSERIAL PRIMARY KEY,
  job_name      TEXT NOT NULL,
  trigger_type  TEXT NOT NULL CHECK (trigger_type IN ('SCHEDULE', 'STARTUP', 'MANUAL')),
  -- The window of business dates the run covered, inclusive.
  window_from   DATE NOT NULL,
  window_to     DATE NOT NULL,
  status        TEXT NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
  items_ok      INT NOT NULL DEFAULT 0,
  items_failed  INT NOT NULL DEFAULT 0,
  detail        TEXT,
  -- Set by the application from its Clock; no database defaults.
  started_at    TIMESTAMPTZ NOT NULL,
  finished_at   TIMESTAMPTZ,
  CONSTRAINT ck_job_run_window CHECK (window_to >= window_from)
);

CREATE INDEX idx_job_run_name_status_to ON job_run (job_name, status, window_to DESC);
CREATE INDEX idx_job_run_started ON job_run (started_at DESC);

CREATE TABLE job_lock (
  job_name      TEXT PRIMARY KEY,
  locked_until  TIMESTAMPTZ NOT NULL,
  locked_by     TEXT NOT NULL
);
