-- ADR 0024 amendment: a person may approve a BUY for fewer shares than proposed, and set a limit
-- price; both are recorded next to the proposal. reference_price is the price the order was sized
-- from (the signal-day close).

ALTER TABLE paper_order ADD COLUMN reference_price NUMERIC(18,4);
ALTER TABLE paper_order ADD COLUMN approved_quantity BIGINT;
ALTER TABLE paper_order ADD COLUMN limit_price NUMERIC(18,4);

ALTER TABLE paper_order ADD CONSTRAINT paper_order_approved_quantity_range
  CHECK (approved_quantity IS NULL OR (approved_quantity > 0 AND approved_quantity <= quantity));
ALTER TABLE paper_order ADD CONSTRAINT paper_order_limit_positive
  CHECK (limit_price IS NULL OR limit_price > 0);

-- Orders proposed before this migration: the signal-day close, from whichever source has it.
UPDATE paper_order o
SET reference_price = (
  SELECT c.close FROM candle c
  WHERE c.instrument_id = o.instrument_id
    AND c.bar_interval = 'DAY'
    AND c.ts = (o.signal_date::timestamp AT TIME ZONE 'Asia/Kolkata')
  ORDER BY c.source
  LIMIT 1);
