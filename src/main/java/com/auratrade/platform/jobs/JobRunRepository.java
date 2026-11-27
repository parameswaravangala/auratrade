package com.auratrade.platform.jobs;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/** job_run history and job_lock leases (V7). Plain JDBC. */
public class JobRunRepository {

  public enum Status {
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED
  }

  public enum Trigger {
    SCHEDULE,
    STARTUP,
    MANUAL
  }

  public record JobRun(
      long id,
      String jobName,
      Trigger trigger,
      LocalDate windowFrom,
      LocalDate windowTo,
      Status status,
      int itemsOk,
      int itemsFailed,
      String detail,
      Instant startedAt,
      Instant finishedAt) {}

  private final JdbcClient jdbc;

  public JobRunRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Takes the job's lease if it is free or expired. Returns false if another holder has it, so the
   * same job never runs twice at once, across threads or instances.
   */
  public boolean tryLock(String job, String owner, Instant now, Duration lease) {
    return jdbc.sql(
                """
                insert into job_lock (job_name, locked_until, locked_by)
                values (:job, :until, :owner)
                on conflict (job_name) do update set
                  locked_until = excluded.locked_until, locked_by = excluded.locked_by
                where job_lock.locked_until <= :now
                """)
            .param("job", job)
            .param("until", Timestamp.from(now.plus(lease)))
            .param("owner", owner)
            .param("now", Timestamp.from(now))
            .update()
        == 1;
  }

  /**
   * Extends a lease this owner still holds. Returns false if the lease was lost or already
   * released, so a heartbeat that fires after {@link #unlock} cannot take the lease back.
   */
  public boolean renew(String job, String owner, Instant now, Duration lease) {
    return jdbc.sql(
                """
                update job_lock set locked_until = :until
                where job_name = :job and locked_by = :owner and locked_until > :now
                """)
            .param("until", Timestamp.from(now.plus(lease)))
            .param("job", job)
            .param("owner", owner)
            .param("now", Timestamp.from(now))
            .update()
        == 1;
  }

  /**
   * Closes runs of {@code job} still marked RUNNING as FAILED. Call only while holding the job's
   * lease: then no live process is running the job, so such rows belong to a process that stopped
   * (killed, crashed, restarted) before it could record an outcome. Returns how many were closed.
   */
  public int closeAbandoned(String job, Instant now) {
    return jdbc.sql(
            """
            update job_run set status = 'FAILED', finished_at = :now,
                   detail = 'abandoned: the process running it stopped before it finished'
                            || coalesce('. ' || detail, '')
            where job_name = :job and status = 'RUNNING'
            """)
        .param("job", job)
        .param("now", Timestamp.from(now))
        .update();
  }

  public void unlock(String job, String owner, Instant now) {
    jdbc.sql("update job_lock set locked_until = :now where job_name = :job and locked_by = :owner")
        .param("now", Timestamp.from(now))
        .param("job", job)
        .param("owner", owner)
        .update();
  }

  public long start(String job, Trigger trigger, LocalDate from, LocalDate to, Instant now) {
    return jdbc.sql(
            """
            insert into job_run (job_name, trigger_type, window_from, window_to, status, started_at)
            values (:job, :trigger, :from, :to, 'RUNNING', :now)
            returning id
            """)
        .param("job", job)
        .param("trigger", trigger.name())
        .param("from", from)
        .param("to", to)
        .param("now", Timestamp.from(now))
        .query(Long.class)
        .single();
  }

  /**
   * Records a run's outcome if it is still RUNNING.
   *
   * @return false if the run was already closed (for example as abandoned); nothing is changed
   */
  public boolean finish(long id, Status status, int ok, int failed, String detail, Instant now) {
    return jdbc.sql(
                """
                update job_run set status = :status, items_ok = :ok, items_failed = :failed,
                                   detail = :detail, finished_at = :now
                where id = :id and status = 'RUNNING'
                """)
            .param("status", status.name())
            .param("ok", ok)
            .param("failed", failed)
            .param("detail", detail)
            .param("now", Timestamp.from(now))
            .param("id", id)
            .update()
        == 1;
  }

  /** Appends text to a run's detail, whatever its status. */
  public void appendDetail(long id, String text) {
    jdbc.sql("update job_run set detail = coalesce(detail, '') || :text where id = :id")
        .param("text", text)
        .param("id", id)
        .update();
  }

  /** End of the latest window that SUCCEEDED or was PARTIAL. */
  public Optional<LocalDate> lastCompletedWindowEnd(String job) {
    return jdbc.sql(
            """
            select max(window_to) from job_run
            where job_name = :job and status in ('SUCCEEDED', 'PARTIAL')
            """)
        .param("job", job)
        .query(LocalDate.class)
        .optional();
  }

  public List<JobRun> recent(int limit) {
    return jdbc.sql(
            """
            select id, job_name, trigger_type, window_from, window_to, status, items_ok,
                   items_failed, detail, started_at, finished_at
            from job_run order by started_at desc, id desc limit :limit
            """)
        .param("limit", limit)
        .query((rs, n) ->
            new JobRun(
                rs.getLong(1),
                rs.getString(2),
                Trigger.valueOf(rs.getString(3)),
                rs.getObject(4, LocalDate.class),
                rs.getObject(5, LocalDate.class),
                Status.valueOf(rs.getString(6)),
                rs.getInt(7),
                rs.getInt(8),
                rs.getString(9),
                rs.getTimestamp(10).toInstant(),
                rs.getTimestamp(11) == null ? null : rs.getTimestamp(11).toInstant()))
        .list();
  }
}
