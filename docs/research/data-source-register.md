# Data source register

- Owner: Chief Data Officer / Market Data Engineer. Reviewers: Compliance, Legal Research. Approver: founder.
- Rule: a source is enabled in a non-personal setting only when **Commercial use** says "Permitted (verified ...)". "Unverified" blocks any external user from seeing data derived from that source. Never bypass paywalls, logins, captchas or rate limits (brief section 9.2).
- Checked 2026-11-27 unless stated. "Unverified" means we have not read the governing terms ourselves.

| ID | Source | Data | Access | Frequency | History | Personal research use | Commercial redistribution | API limits | Status |
|---|---|---|---|---|---|---|---|---|---|
| S-01 | Upstox instrument file (public) | NSE instrument master | public HTTPS, no token (ADR 0015) | daily | current only | in use since 2026-11-26 | **Unverified**: read Upstox API terms | n/a | IN USE (personal) |
| S-02 | Upstox v3 historical + OHLC quote API | daily candles, session bars | Analytics token (`.env`) | EOD + backfill | from about 2000 for daily; before 2018-12 restamped | in use | **Unverified, likely not permitted**: broker data is usually licensed for the account holder's own use. This is the main SaaS blocker (risk R-01) | published limits, we run at 80 % (ADR 0016); token `exp` 2027-11-26 | IN USE (personal) |
| S-03 | NSE holiday list and bhavcopy archive | calendar reconciliation | public web | yearly / daily | 2018+ reconciled | used for reconciliation only (runbook) | NSE's Data Usage and Data Sharing Policy says NSE is the sole owner of market data and licenses use and redistribution through NSE Data ([policy PDF](https://nsearchives.nseindia.com/web/sites/default/files/inline-files/NSE_Data_Sharing%26Usage_Policy.pdf), [policy page](https://www.nseindia.com/market-data/nse-data-policy)). **Redistribution needs an NSE Data licence** | site terms; no automated scraping without checking the terms | IN USE (manual reconciliation) |
| S-04 | NSE corporate announcements | filings, board outcomes, results | nseindia.com pages/JSON used by the site | intraday | years | **Unverified**: the site terms on automated access must be read before any polling (story R2-01) | **Unverified**: NSE policy above likely applies to derived data too (its non-display policy mentions derived data) | unknown; anti-bot protection reported, which we must not circumvent | NOT ENABLED |
| S-05 | BSE corporate announcements | filings | bseindia.com | intraday | years | **Unverified** | **Unverified** | unknown | NOT ENABLED |
| S-06 | NSE "Data Sharing Policy for Research and Analysis" data list | research datasets | application to NSE | n/a | n/a | possible route for research; [data list PDF, 2025-07-28](https://nsearchives.nseindia.com/web/sites/default/files/inline-files/Data%20list%20under%20NSE%20Data%20Sharing%20Policy%20for%20Research%20and%20Analysis_20250728.pdf) | likely not commercial | n/a | TO EVALUATE |
| S-07 | NSE Data & Analytics licensed EOD/historical feed | official EOD, corporate actions | paid licence + undertaking (ADR 0012) | daily | long | yes | **yes under licence; price unknown** (quote needed, founder approval) | per contract | CANDIDATE for SaaS |
| S-08 | Company IR pages / annual reports (PDF) | financial statements | public | quarterly | years | yes for personal reading | copyright of the issuer; facts are usable, text is not. Store excerpts ≤ 300 chars | polite crawl, robots.txt | TO EVALUATE |
| S-09 | RBI publications (policy, DBIE) | rates, macro | public | scheduled | long | yes | **Unverified**: check the RBI website terms | n/a | TO EVALUATE |
| S-10 | MoSPI (CPI, IIP, GDP) | macro releases | public, release calendar | monthly/quarterly | long | yes | **Unverified**: Government Open Data Licence India may apply to data.gov.in datasets (check the dataset licence) | n/a | TO EVALUATE |
| S-11 | News RSS of publishers | headlines + snippets | RSS | minutes | short | headline + link likely acceptable for personal use | **Unverified per publisher**. Store headline, link and our own summary; never the full article | per feed | TO EVALUATE |
| S-12 | Licensed news API (vendor TBD) | full news | paid | realtime | varies | yes | yes under licence | per plan | CANDIDATE (cost approval) |
| S-13 | Zerodha Kite Connect | ticks, historical, orders | INR 500/month per ADR 0012 (from Zerodha's support page, read then) | realtime | | yes | personal-use licence assumed; **unverified** | 3 req/s historical (unverified) | RETIRED loader (B-022 parked) |

## Per-source checklist (fill before enabling)

1. Governing terms URL and date read; quote the relevant clause (≤ 15 words) in the row.
2. Automated access allowed? Rate limits?
3. Storage allowed? How long?
4. Display to third parties allowed? Derived data allowed?
5. Attribution required?
6. Cost and plan.
7. Data quality checks (completeness, timeliness, known gaps).
8. Fallback source.
