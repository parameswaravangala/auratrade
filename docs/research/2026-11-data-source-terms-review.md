# Data source terms review (story R2-01)

- Owner: Market Data & Licensing (Chief Data Officer, Market Data Engineer, Data Governance). Reviewers: Compliance, Legal Research. Approver: founder.
- Read on 2026-11-27 with an automated page reader (plain GET, no login, no forms, no contact forms). When a site refused or did not answer automated access, this review records that and goes no further. It does not retry from another host, browser or user agent.
- Input: `docs/research/data-source-register.md` on branch `docs/phase-0-reset`, rows S-01 to S-13.
- This is not legal advice. Anything marked **unverified** needs a human to read the page in a normal browser, or needs counsel.
- Quotes are at most one per source and under 15 words.

## Answer on R-01 (may SaaS customers see Upstox or NSE prices or derived data?)

**No. Not on the evidence we can read today.** Treat every Upstox-sourced price, and anything computed from it, as founder-only (`may_display_external = false`) until a licence says otherwise.

- Upstox publishes no data licence for its Developer API. Its site-wide Terms of Use and Privacy Policy has no API or market-data clause. The free Analytics Token page covers access, not redistribution. The only Upstox text on redistribution is `ai.txt`, and it bars "bulk data licensing for resale, or republishing as standalone product". The community replies (2024-2025) are not labelled as staff and are not binding. The one reply that is specific about third parties says "only Upstox customers" should consume the data. One reply says derived data (MACD) "you can use", but it does not address the exchange's rights.
- NSE owns the underlying market data. The Phase 0 register (row S-03) records that NSE's Data Usage and Data Sharing Policy licenses redistribution through NSE Data & Analytics. Every NSE URL timed out for us three times, so this review cannot add a quote on derived data. A broker's terms cannot grant rights the exchange has not granted to the broker.
- Zerodha's Kite Connect terms are explicit and point the same way: no public display, no databases, no derivative works (S-13). This is the closest published analogue for broker API data.

**Options to unblock SaaS** (all cost money, so each needs founder approval; nothing is signed up or requested):

| Option | What its own published page says | Gap |
|---|---|---|
| A. NSE Data & Analytics EOD / historical licence (S-07) | Tariff PDFs exist on nsearchives. Not read: timed out | Fees, display and derived-data terms unread. Needs a human read of the April 2026 domestic pricing file |
| B. Authorised vendor: TrueData ([truedata.in](https://www.truedata.in/products/marketdataapi), read 2026-11-27) | States it is an NSE, BSE and MCX authorised vendor. Redistribution or public display: "contact" them, exchange approvals where needed | No published SaaS or redistribution plan or price. Velocity desktop plans from INR 1,439.83/month per segment (`/price`), not for redistribution |
| C. Authorised vendor: Global Datafeeds ([globaldatafeeds.in](https://globaldatafeeds.in/global-datafeeds-apis/global-datafeeds-apis/pricing-sales/who-can-purchase/), read 2026-11-27) | "Authorised Realtime (L1) Data Vendor of NSE". APIs are for individual personal use. Commercial use needs your own exchange agreement and fees | No redistribution plan. Prices are on request |
| D. Display only what needs no exchange licence | Macro data under GODL (S-10, if confirmed), our own text, links and headlines (S-11, per publisher) | Does not cover prices, which a trading product needs |

None of the vendor pages we read publishes a ready-made SaaS redistribution licence. Every route ends in an exchange agreement (option A), directly or through the vendor. **Recommended next step:** the founder (not an agent) reads the NSE April 2026 domestic pricing PDF and the NSE data policy in a browser, then decides whether to request a quote from NSE Data & Analytics. Until then, SaaS can show only non-market-data features, and any user other than the founder sees no prices.

## Summary table (fields of ADR 0034's `data_source` registry)

`may_store_days`: `null` = not stated by any page we read; `0` = must not store. "unverified" means the terms were not read or do not say.

| code | kind | terms_url (read 2026-11-27) | may_automate | may_store_days | may_display_external | may_derive | attribution_text | cost_note | status |
|---|---|---|---|---|---|---|---|---|---|
| `UPSTOX_INSTRUMENTS` (S-01) | REFERENCE | https://upstox.com/developer/api-documentation/instruments/ | yes (published for programmatic download; robots.txt does not disallow) | null (not stated) | false (no grant; ai.txt bars republishing) | internal only | none stated | free | personal only |
| `UPSTOX` (S-02) | PRICE | https://upstox.com/ai.txt ; https://upstox.com/developer/api-documentation/rate-limiting/ ; https://upstox.com/privacy-policy/ | yes, within published limits | null (not stated for API; ai.txt caps AI caching of prices at 5 minutes) | false | internal only (community "you can use it" is not binding) | none stated | free Analytics Token | personal only; blocks SaaS (R-01) |
| `NSE_WEB` (S-04) | DOCUMENT | https://www.nseindia.com/static/nse-terms-of-use (timed out x3) | **unverified**: do not automate | unverified | false | unverified | unverified | n/a | terms refused automated access; NOT ENABLED |
| `BSE_WEB` (S-05) | DOCUMENT | https://www.bseindia.com/disclaimer.html , /termsofuse.html (HTTP 403) | **unverified**: do not automate | unverified | false | unverified | unverified | n/a | terms refused automated access; NOT ENABLED |
| `NSE_RESEARCH` (S-06) | PRICE | data list PDF 2025-07-28 (timed out x2) | unverified | unverified | false | unverified | unverified | unverified | terms refused automated access; TO EVALUATE |
| `NSE_DATA` (S-07) | PRICE | 2026-03 domestic pricing PDF, 2026-04 EOD tariff PDF (timed out x3) | per contract | per contract | per contract (the only clear SaaS route) | per contract | per contract | unverified: tariff unread | CANDIDATE; needs founder read and approval |
| `RBI` (S-09) | MACRO | https://www.rbi.org.in/Scripts/Disclaimer.aspx | not addressed; RBI may restrict access | unclear (the hyperlinking clause bars "caching"; scope ambiguous) | yes, reproduced accurately and unmodified with acknowledgement | not granted ("without modification") | "Source: Reserve Bank of India" (wording ours; terms say "appropriate acknowledgement") | free | usable with care; derived charts need counsel |
| `MOSPI` / `OGD` (S-10) | MACRO | https://www.mospi.gov.in/website-policies (empty body); https://data.gov.in/government-open-data-license-india (HTTP 403) | unverified | unverified | unverified | unverified | unverified | free (unverified) | terms not found or refused automated access |
| `NEWS_RSS_*` (S-11) | DOCUMENT | ET, Moneycontrol, Livemint, BusinessLine (reader refused host); Business Standard (HTTP 403) | unverified | unverified | false | unverified | unverified | free feeds (unverified) | terms refused automated access; NOT ENABLED |
| `KITE` (S-13) | PRICE | https://kite.trade/terms ; https://support.zerodha.com/.../can-i-use-historical-and-live-data-taken-from-kite-connect-api-on-other-platforms | yes, 3 req/s historical | 0 (no "permanent copies" or databases without permission) | false | false ("derivative work" barred) | do not misrepresent source | INR 500/month per API key, historical included | RETIRED; would not help SaaS |

S-03 (NSE archive, manual reconciliation), S-08 (company IR) and S-12 (licensed news API, vendor TBD) were not part of this story's list. They keep their register rows.

## Per-source sections

Each section answers the register's checklist: 1 terms and quote, 2 automated access, 3 storage, 4 display and derived data, 5 attribution, 6 cost, 7 quality, 8 fallback.

### S-01 Upstox instrument file (public)

1. Terms: [instruments docs](https://upstox.com/developer/api-documentation/instruments/), [ai.txt v1.0](https://upstox.com/ai.txt) (last modified 2025-11-03), [Terms of Use and Privacy Policy](https://upstox.com/privacy-policy/) (updated 2026-08-15), [robots.txt](https://upstox.com/robots.txt). All read 2026-11-27. `https://upstox.com/terms-and-conditions/` returns 404. The instruments page states no licence. The `.gz` files on assets.upstox.com were not fetched.
2. Automated access: the file is published for programmatic download. robots.txt (`User-agent: *`) disallows only `/wp/wp-admin/` and search query paths.
3. Storage: not stated.
4. Display and derived data: not granted. ai.txt reserves "all rights in the underlying content and data". Instrument facts such as ISIN and tick size are low risk, but we have no grant. Use internally only.
5. Attribution: none stated for API data. ai.txt asks AI systems for links and `utm_source` tags, which is not our case.
6. Cost: free, no token.
7. Quality: the terms disclaim accuracy and completeness ("entirely at your own risk"). Our loader checks ISIN check digits and rejects bad rows (ADR 0015; first load had 1 bad ISIN).
8. Fallback: NSE Data & Analytics reference data under licence (S-07), or the Kite instrument dump (retired; same personal-use limits).

### S-02 Upstox v3 historical candles and OHLC quotes

1. Terms: as S-01, plus [rate limits](https://upstox.com/developer/api-documentation/rate-limiting/) and [Analytics Token](https://upstox.com/developer/api-documentation/analytics-token/), read 2026-11-27. Community threads [4469](https://community.upstox.com/t/commercialization-of-application-based-on-api-data/4469), [4491](https://community.upstox.com/t/can-i-use-upstox-api-data-on-my-website-to-display-for-commercial-uses/4491), [3772](https://community.upstox.com/t/upstox-api-legal-or-not/3772) and [18152](https://community.upstox.com/t/18152) were also read. They carry no visible staff labels, are not binding, and the October 2026 licensing question in 18152 is unanswered. Thread 17896 ("Permission to Display Upstox Market Data in a Mobile Application") was not read; the next review should read it. Quote (ai.txt): "Not Allowed: Bulk data licensing for resale, or republishing as standalone product".
2. Automated access: yes. "Other Standard APIs" (including historical candles): 50/s, 500/min, 2000 per 30 min. Exceeding them may suspend access. Quote endpoints take up to 500 instruments per call (llms.txt). We run at 80 % (ADR 0016).
3. Storage: not stated for API responses. ai.txt caps AI-system caching of real-time prices at 5 minutes. That rule is aimed at AI crawlers, and whether it applies to API data is **unverified**. Our multi-year candle store has no explicit grant.
4. Display and derived data: no grant to show data to third parties. One community reply recommends only Upstox customers consume it. On derived data, a 2025-04-22 community reply says "you can use it" for MACD in a commercial app. It is not binding and does not address NSE's rights. ai.txt also bars generating trading signals without Upstox's authorisation (aimed at AI systems; relevance **unverified**).
5. Attribution: none stated.
6. Cost: the Analytics Token is free (one-year, read-only).
7. Quality: accuracy and timeliness disclaimed. Our `CandleValidator` and the 90 % coverage gate apply. Known gap: daily bars before 2018-12 restamped.
8. Fallback: an authorised vendor (TrueData, Global Datafeeds) or NSE Data & Analytics, both paid. Kite needs INR 500/month and has stricter terms.

### S-04 NSE website terms and corporate announcements

1. Terms: [NSE Terms of Use](https://www.nseindia.com/static/nse-terms-of-use), [website policies](https://www.nseindia.com/static/website-policies), [data policy page](https://www.nseindia.com/static/market-data/nse-data-policy), [Data Sharing & Usage Policy PDF](https://nsearchives.nseindia.com/web/sites/default/files/inline-files/NSE_Data_Sharing%26Usage_Policy.pdf), [Non-Display Policy PDF](https://nsearchives.nseindia.com/web/sites/default/files/inline-files/Non_Display_Policy.pdf), [disclaimer](https://www.nseindia.com/nse-disclaimer). **Every URL timed out on all three attempts on 2026-11-27. Terms refused automated access.** No quote.
2. Automated access: **unverified, so do not poll**. A search-engine snippet (not our own read) suggests the Terms of Use forbid "systematic or automated data collection". If a human confirms that, S-04 can never be scraped. Corporate announcements would then need a licensed route, an NSE corporate-data subscription or a vendor.
3–6. Unverified. NSE's ownership of market data is recorded in register S-03.
7. Quality: n/a.
8. Fallback: BSE (S-05, also unread), company IR pages (S-08), or the NSE corporate-data subscription (paid, unread).

### S-05 BSE website terms

1. Terms: [disclaimer](https://www.bseindia.com/disclaimer.html) and [terms of use](https://www.bseindia.com/termsofuse.html). **HTTP 403 on 2026-11-27. Terms refused automated access.** The BSE RSS page was not tried after the host refused.
2–6. Unverified. A 403 to a plain reader suggests automated access to the site is unwelcome. Do not enable.
7–8. n/a. Fallback: S-08.

### S-06 NSE Data Sharing Policy for Research and Analysis

1. Terms: [data list PDF, 2025-07-28](https://nsearchives.nseindia.com/web/sites/default/files/inline-files/Data%20list%20under%20NSE%20Data%20Sharing%20Policy%20for%20Research%20and%20Analysis_20250728.pdf). **Timed out twice on 2026-11-27. Terms refused automated access.**
2–6. Unverified. A search snippet (not read by us) suggests eligibility is limited to accredited academic institutions and research organisations. A solo founder may not qualify (**unverified**). It is also unlikely to be commercial.
7–8. n/a. Fallback: S-07.

### S-07 NSE Data & Analytics products (tariffs)

1. Terms: [Domestic pricing file, 2026-03](https://nsearchives.nseindia.com//web/mediaattachment/2026-03/NSE_Pricing_file_-_Domestic_clients_20260309171343.pdf), [EOD data tariff, 2026-04](https://nsearchives.nseindia.com//web/mediaattachment/2026-04/Download_End_of_the_Day_Data_Tariff_20260424120810.pdf), [EOD and historical subscription page](https://www.nseindia.com/static/market-data/eod-historical-data-subscription). **All timed out (three attempts on the pricing file) on 2026-11-27. Not read.** No contact forms used.
2–5. Per contract.
6. Cost: **unverified**. A search snippet mentions an annual EOD fee per segment, per site and per medium, but we did not read the document. Do not budget from it.
7. Quality: exchange-official. This is the reference for corporate actions (B-020).
8. Fallback: authorised vendors (see R-01 table).

### S-09 RBI

1. Terms: [RBI Disclaimer, copyright, hyperlinking and security policies](https://www.rbi.org.in/Scripts/Disclaimer.aspx), read 2026-11-27 (page footer "last updated Nov 27, 2026"). `WebsitePolicies.aspx` returned an error page. DBIE (`data.rbi.org.in`) returned no readable terms. Quote: "may be reproduced accurately and without modification, subject to appropriate acknowledgement".
2. Automated access: not addressed. RBI may deny or restrict access and block IPs. Use a low rate.
3. Storage: the hyperlinking clause prohibits "caching" and framing. It sits under "links from other web sites", so its scope is ambiguous (**unverified**).
4. Display: reproduction is allowed if accurate and unmodified, with acknowledgement. Derived works are not granted. Third-party material on the site is excluded.
5. Attribution: "appropriate acknowledgement of the RBI as the source". No exact wording given.
6. Cost: free.
7. Quality: provided "as is", with no responsibility for accuracy or completeness.
8. Fallback: MoSPI (S-10) for some series. Policy-rate facts are also in RBI press releases.

### S-10 MoSPI and data.gov.in (GODL)

1. Terms: [MoSPI website policies](https://www.mospi.gov.in/website-policies) returned a title-only body (likely rendered by script). [GODL page](https://data.gov.in/government-open-data-license-india) and the [gazette PDF](https://data.gov.in/sites/default/files/Gazette_Notification_OGDL.pdf) returned **HTTP 403**. **Terms not found or refused automated access.** The MoSPI API (eSankhyiki) was not checked.
2–6. Unverified. Secondary sources (not read at the source) describe GODL as allowing commercial reuse with attribution. If a human confirms that from the gazette, S-10 becomes the cleanest SaaS-displayable source.
7–8. n/a. Fallback: RBI DBIE for overlapping series.

### S-11 Business-news RSS (three publishers)

1. Terms tried on 2026-11-27: The Economic Times ([RSS](https://economictimes.indiatimes.com/rss.cms), [terms](https://economictimes.indiatimes.com/termsofuse.cms)), Moneycontrol ([RSS](https://www.moneycontrol.com/rss/)), Livemint ([RSS](https://www.livemint.com/rss)) and The Hindu BusinessLine ([RSS](https://www.thehindubusinessline.com/rssfeeds/)) were all refused by our reader for that host. Business Standard ([RSS listing](https://www.business-standard.com/rss-feeds/listing)) returned **HTTP 403**. **Terms refused automated access for all of them.**
2–6. Unverified for every publisher. Register policy stands: store at most the headline, link and our own summary, never the article body, and show nothing externally until each publisher's terms are read.
7. Quality: an RSS headline is untrusted input (prompt-injection case n21).
8. Fallback: a licensed news API (S-12, paid) or company filings (S-08).

### S-13 Zerodha Kite Connect

1. Terms: [Kite Connect terms](https://kite.trade/terms) (no version date shown), [support article on other platforms](https://support.zerodha.com/category/trading-and-markets/kite-api/articles/can-i-use-historical-and-live-data-taken-from-kite-connect-api-on-other-platforms), [product page](https://zerodha.com/products/api/), [rate limits](https://kite.trade/docs/connect/v3/exceptions/), [payment-plan article](https://support.zerodha.com/category/trading-and-markets/general-kite/kite-api/articles/historical-data-and-live-market-data-payment-plan). All read 2026-11-27. Quote (clause 4(b)): "Scrape, build databases, or otherwise create permanent copies of such content".
2. Automated access: yes, by documented means. Historical candles 3 req/s, quotes 1 req/s, others 10 req/s.
3. Storage: no permanent copies or databases without express permission (4(b)). Cached content must be deleted on termination (9(b)).
4. Display and derived data: live data "cannot be displayed to the public at large" (2(a)). No distribution, public display or derivative works (4(b)). The support article says display on other platforms violates exchange data-vending policies and needs an authorised vendor.
5. Attribution: do not misrepresent the source or remove notices.
6. Cost: INR 500/month per API key, with historical and live data included. The free Personal plan has no market data.
7. Quality: no warranty. Liability is capped at INR 100 (7(c)).
8. Fallback: n/a. Retired, and storage limits make it unsuitable even for our personal multi-year store.

## Follow-ups

- A human (the founder) reads the NSE Terms of Use, data policy, Non-Display Policy and the April 2026 domestic tariff in a browser. This settles S-04, S-06 and S-07 and the derived-data question in R-01.
- A human reads the GODL gazette, the BSE terms and one publisher's RSS terms in a browser.
- Read Upstox community thread 17896 (display in a mobile app).
- Once ADR 0034 is accepted, seed `data_source` rows from the summary table above, with `may_display_external = false` wherever it says unverified.
