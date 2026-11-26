#!/usr/bin/env bash
# Records, for every date FROM..TO, whether NSE published a Capital Market bhavcopy that day
# (HTTP 200 = traded, 404 = closed). Official evidence from 2024-01-01 onwards only: the new-format
# archive starts then. Polite: one HEAD request per date, 0.25 s apart. Run in Git Bash.
# Usage: scripts/calendar/probe-nse-trading-days.sh 2027-01-01 2027-12-31 days.csv
d="$1"; end="$2"; out="$3"; : > "$out"
while [ "$(date -d "$d" +%Y%m%d)" -le "$(date -d "$end" +%Y%m%d)" ]; do
  ymd=$(date -d "$d" +%Y%m%d)
  code=$(curl -s -o /dev/null -I --max-time 20 -A "Mozilla/5.0" -w "%{http_code}" \
    "https://nsearchives.nseindia.com/content/cm/BhavCopy_NSE_CM_0_0_0_${ymd}_F_0000.csv.zip")
  echo "$(date -d "$d" +%F),$(date -d "$d" +%a),$code" >> "$out"
  sleep 0.25
  d=$(date -d "$d + 1 day" +%F)
done

