# Usage: grep -hE "^(HOLIDAY|SPECIAL)," nse-calendar.csv > cal.csv; awk -F, -f reconcile.awk cal.csv days.csv
# Inputs: 1) calendar rows "TYPE,date,..." (draft + live calendar), 2) days.csv "date,dow,http".
# Prints every date where the archive and the calendar disagree. 200 = traded, 404 = closed.
FNR==NR { if ($1=="HOLIDAY") hol[$2]=1; if ($1=="SPECIAL") spec[$2]=1; next }
{
  d=$1; dow=$2; code=$3; weekend=(dow=="Sat"||dow=="Sun")
  if (code!="200" && code!="404") { print "UNKNOWN  " d " " dow " http=" code; next }
  traded=(code=="200")
  expected = spec[d] ? 1 : (weekend ? 0 : !hol[d])
  if (traded != expected) {
    if (traded && weekend)      print "WEEKEND-SESSION   " d " " dow " (archive has a file; no SPECIAL row)"
    else if (traded)            print "TRADED-ON-HOLIDAY " d " " dow " (listed HOLIDAY but archive has a file)"
    else if (spec[d])           print "SPECIAL-NO-FILE   " d " " dow " (SPECIAL row but no file)"
    else                        print "UNLISTED-CLOSURE  " d " " dow " (weekday, no file, not a listed holiday)"
  }
}
