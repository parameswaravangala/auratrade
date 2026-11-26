package com.auratrade.api;

import com.auratrade.platform.jobs.JobRunRepository.JobRun;
import com.auratrade.platform.jobs.JobRunRepository.Trigger;
import com.auratrade.platform.jobs.JobRunner;
import com.auratrade.platform.jobs.ScheduledJob;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dev-only: run a job by hand and read the run history. */
@RestController
@Profile("dev")
@RequestMapping("/dev/jobs")
class JobsDevController {

  private final JobRunner runner;
  private final Map<String, ScheduledJob> jobs;

  JobsDevController(JobRunner runner, List<ScheduledJob> jobs) {
    this.runner = runner;
    this.jobs = jobs.stream().collect(Collectors.toMap(ScheduledJob::name, Function.identity()));
  }

  /** Runs whatever the job still owes, for example POST /dev/jobs/eod-candles/run. */
  @PostMapping("/{name}/run")
  Map<String, String> run(@PathVariable String name) {
    ScheduledJob job = jobs.get(name);
    if (job == null) {
      throw new IllegalArgumentException("unknown job '" + name + "'; known: " + jobs.keySet());
    }
    return Map.of("job", name, "result", runner.run(job, Trigger.MANUAL).name());
  }

  @GetMapping("/runs")
  List<JobRun> runs(@RequestParam(defaultValue = "20") int limit) {
    return runner.recent(Math.min(limit, 200));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
