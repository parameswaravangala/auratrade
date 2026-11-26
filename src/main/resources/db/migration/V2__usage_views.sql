-- Human-friendly spend views (IST days and months). Handy in psql now, Grafana later.
CREATE VIEW llm_usage_daily AS
SELECT (ts AT TIME ZONE 'Asia/Kolkata')::date AS day_ist,
       agent,
       tier,
       count(*)                          AS runs,
       sum(input_tokens)                 AS input_tokens,
       sum(output_tokens)                AS output_tokens,
       sum(coalesce(cache_read, 0))      AS cache_read,
       sum(coalesce(cache_write, 0))     AS cache_write,
       sum(cost_usd)                     AS cost_usd
FROM llm_usage
GROUP BY 1, 2, 3;

CREATE VIEW llm_usage_monthly AS
SELECT date_trunc('month', ts AT TIME ZONE 'Asia/Kolkata')::date AS month_ist,
       agent,
       tier,
       count(*)                          AS runs,
       sum(input_tokens + output_tokens + coalesce(cache_read, 0) + coalesce(cache_write, 0)) AS total_tokens,
       sum(cost_usd)                     AS cost_usd
FROM llm_usage
GROUP BY 1, 2, 3;
