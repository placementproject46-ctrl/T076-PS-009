# PS-009 – Stock Brokerage Margin, Settlement and Pledge Engine — Sample Data Pack

Prepared by 10 SECONDS for Team T076.

## Files

| File | What it holds | Row count |
|---|---|---|
| `clients.csv` | 6 clients with opening ledger cash on 2026-09-30 | 6 data rows |
| `holdings_opening.csv` | Demat holdings and pledged quantity on 2026-09-30 | 24 data rows |
| `scrips.csv` | 15 scrips with pledge group (haircut class) | 15 data rows |
| `prices.csv` | Closing prices: base day 2026-09-30 + 20 trading days of October 2026 | 315 data rows |
| `holidays.csv` | Exchange holidays Sep–Nov 2026 | 5 data rows |
| `commands.txt` | 90 orders, 10 pledge/unpledge requests, funds-in, a re-tag, price marks, daily SETTLE / square-off / EOD and report requests | 173 command lines (excluding comments) |

Row numbers quoted below are 1-based data-row numbers (the header is row 0). Line numbers for `commands.txt` are physical line numbers including comment lines.

## Column reference

Conventions: dates `YYYY-MM-DD`; timestamps `YYYY-MM-DD HH:MM:SS` (24h, IST, no timezone suffix); money in Rs as plain decimals with 2 places (no commas, no symbol); percentages as plain numbers (12.5 means 12.5%); booleans `true`/`false`; IDs uppercase with a fixed prefix. An empty cell means "not applicable" and only occurs in columns whose notes say so.

### `clients.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| client_id | string | CL-nn | — | — |
| client_name | string | — | — | — |
| pan | string | AAAAA9999A | — | — |
| mobile | string | 10 digits | — | — |
| opening_cash | decimal | Rs | — | free ledger balance after all settlements up to opening_date |
| opening_date | date | — | — | — |

### `holdings_opening.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| client_id | string | — | clients.csv | — |
| symbol | string | — | scrips.csv | — |
| quantity | int | shares | — | settled holding incl. pledged |
| pledged_qty | int | shares | — | already pledged as collateral |
| avg_cost | decimal | Rs/share | — | info only |

### `scrips.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| symbol | string | — | — | primary key |
| company_name | string | — | — | — |
| pledge_group | enum | — | GROUP_A, GROUP_B, GROUP_C | initial tag; RETAG commands change it |
| lot_size | int | shares | 1 | cash segment |

### `prices.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| trade_date | date | — | — | 2026-09-30 is the base day (previous close for 2026-10-01) |
| symbol | string | — | scrips.csv | — |
| close_price | decimal | Rs/share | — | — |

### `holidays.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| date | date | — | — | — |
| holiday_name | string | — | — | — |

## Command file format

`commands.txt` holds one command per line in the form `COMMAND_NAME|arg1|arg2|...`. Commands must be processed strictly in file order. Lines starting with `#` are comments and blank lines are ignored. Every command used in this pack:

| Command | Arguments (in order) | Meaning |
|---|---|---|
| `ORDER` | `order_id|timestamp|client_id|symbol|side|product|quantity|price` | side BUY/SELL, product DELIVERY/INTRADAY; an accepted order executes fully at `price` |
| `PLEDGE` | `request_id|timestamp|client_id|symbol|quantity` | Pledge free holdings as collateral (Rs 20 charge per request) |
| `UNPLEDGE` | `request_id|timestamp|client_id|symbol|quantity` | Release pledged quantity |
| `FUNDS_IN` | `request_id|timestamp|client_id|amount` | Client pays money into the ledger |
| `RETAG` | `timestamp|symbol|new_group` | Exchange/RMS changes the scrip's pledge group with immediate effect |
| `PRICE_UPDATE` | `timestamp|symbol|price` | Intraday price mark; re-run MTM for every client with an open INTRADAY position |
| `SETTLE` | `date` | 08:00: settle all T+1 obligations falling on this date |
| `INTRADAY_SQUARE_OFF` | `timestamp` | 15:20: auto-close every open INTRADAY position at that day's close_price, Rs 50 penalty per position |
| `EOD` | `date` | 16:00: MTM at close, collateral revaluation, negative-balance interest, end-of-day report |
| `PRINT_REPORT` | `client_id|date` | Print ledger, holdings and margin as at EOD of that date |

## Planted scenarios

Every edge case in the brief, mapped to the exact record where it occurs, followed by extra traps.

- **EC-01 Sell on Friday, buy Monday using exactly 80% then 100% of proceeds**
  - CL-01 (cash 0.00): SELL 200 TUNGAFIN @ 1250.00 = Rs 2,50,000 DELIVERY on Friday 2026-10-09; BUY Rs 2,00,000 the same day = exactly 80% of the sale value (accepted at the boundary); one more share that afternoon exceeds 80% (reject); on Monday after SETTLE the BUY of Rs 50,000 brings purchases to 100% of the gross proceeds — usable cash is the settled credit minus the charges of both legs, so check the shortfall — `commands.txt line 30, line 32, line 33, line 37`
- **EC-02 Collateral covering exactly 50% of required margin**
  - CL-02: 100 SAHYADRI pledged, previous close 2000.00 -> collateral 2,00,000 x (1 - 10%) = Rs 1,80,000; INTRADAY BUY 1000 KAVERITEX @ 1800.00 needs 20% = Rs 3,60,000 -> collateral covers exactly 50% and cash Rs 1,80,000 the other 50% (accept) — `holdings_opening.csv CL-02 (row 8); prices.csv row 106; commands.txt line 42`
- **EC-03 Intraday position opened at 15:19 and MTM triggered at 15:20**
  - CL-03 (cash Rs 2,00,000): INTRADAY BUY 1000 NANDIPWR @ 1000.00 at 2026-10-15 15:19:00 uses the full 20% margin; the 15:20:00 price mark 950.00 (loss Rs 50,000) breaches 80% and arrives on the same second as the 15:20 auto square-off — `commands.txt line 56, line 57, line 58`
- **EC-04 Stock re-tagged from GROUP_A to GROUP_C while pledged**
  - CL-04 has 200 KARWARPRT pledged (GROUP_A, 10% haircut); RETAG to GROUP_C at 2026-10-16 12:00 -> its collateral value drops to nil; the 13:10 INTRADAY order needing about Rs 1,00,000 margin (cash Rs 60,000 + collateral capped at 50%) would have passed before the re-tag and fails after it; then UNPLEDGE of the 200 at 14:00 — `holdings_opening.csv CL-04 (row 15); commands.txt line 61, line 62, line 64`
- **EC-05 Order at exactly +10.00% price band**
  - CL-06: CHAMUNDI previous close 500.00 (2026-10-21); BUY @ 550.00 = exactly +10.00% (inside the band, accept); @ 550.05 = +10.01% (reject); @ 450.00 = exactly -10.00% (accept) — `prices.csv row 201; commands.txt line 93, line 94, line 95`
- **EC-06 Ledger goes negative by Rs 1 for one day**
  - CL-05 cash Rs 10,01,190.71; DELIVERY BUY 400 KRISHNAIT @ 2500.00 = Rs 10,00,000 on 2026-10-06: charges STT 1,000.00 + exchange 34.50 + SEBI 1.00 + stamp 150.00 + GST 6.21 = Rs 1,191.71 -> T+1 debit on 2026-10-07 leaves the ledger at -1.00; FUNDS_IN Rs 5,000 on 2026-10-08 10:00 clears it (interest from the next day only) — `clients.csv CL-05 (row 5); commands.txt line 14, line 22`
- **EC-07 Pledge requested on a holiday**
  - CL-04: PLEDGE of 200 COORGCOF timestamped 2026-10-20 11:00 — Dussehra, a market holiday (no SETTLE/EOD that day); the brief does not say whether to reject or queue it to the next business day — `commands.txt line 80; holidays.csv 2026-10-20`
- **EC-08 Multiple positions tied for largest at MTM square-off**
  - CL-06 (cash Rs 2,00,000 = 20% of Rs 10,00,000): INTRADAY MALNADAGR 1000 @ 500.00 and MYSOREBNK 500 @ 1000.00 on 2026-10-05; both marked down 7% -> losses Rs 70,000 take margin to Rs 1,30,000 < 80% of Rs 2,00,000 -> MTM; both positions are worth Rs 4,65,000 = tied for largest. Tie-break for evaluation: the position opened earlier (lower order_id) is squared off first — `clients.csv CL-06 (row 6); commands.txt lines 7, 8, price marks lines 9, 10`
- **Extra traps (not in the brief, but realistic):**
  - CL-04: PLEDGE of a GROUP_C scrip (BIDARCHEM) must be REJECTED (not pledgeable); the Rs 20 charge should not apply — `commands.txt line 71`
  - CL-02: SELL 60 UDUPIPHRM with only 50 free (150 held, 100 pledged) at a price far outside the band -> REJECT listing BOTH the holdings and price-band rule IDs — `commands.txt line 117`
  - CL-03: tiny INTRADAY order (50 GADAGSUG, value about Rs 2,000) -> 0.03% is below Rs 1, so brokerage = Rs 1 minimum — `commands.txt line 120`
  - CL-06: DELIVERY BUY brokerage is Rs 0 (no Rs 1 minimum on DELIVERY); stamp 0.015% on the buy leg — `commands.txt line 130`
  - CL-02: UNPLEDGE of 120 SAHYADRI when only 100 are pledged -> REJECTED (only free pledged quantity) — `commands.txt line 145`

## What is NOT in this pack

Expected outputs are not provided for this set. Validate your engine with your own test suite against the rules in your brief (BR-01 … BR-nn). A separate hidden input set with the same schema will be used at final evaluation; outputs are compared field-for-field, so follow the column formats above exactly in your own output.

## Output format expected at evaluation

Produce these CSV files (header row, same conventions as above, rows sorted as stated). Where the brief is silent on a format detail the choice below is binding for evaluation.

### `order_decisions.csv`

One row per ORDER, PLEDGE and UNPLEDGE line, in file order; auto square-offs get their own rows with order_id SQ-<client_id>-<symbol>-<date>.

Columns, in order: `ref_id`, `timestamp`, `client_id`, `symbol`, `action`, `decision`, `rule_ids`, `quantity`, `price`, `trade_value`, `required_margin`, `available_margin`

### `contract_notes.csv`

One row per executed order (including square-offs).

Columns, in order: `ref_id`, `trade_date`, `client_id`, `symbol`, `side`, `product`, `quantity`, `price`, `trade_value`, `brokerage`, `stt`, `exchange_charge`, `sebi_fee`, `stamp_duty`, `gst`, `penalty`, `total_charges`, `net_amount`

### `eod_report.csv`

One row per client per EOD.

Columns, in order: `date`, `client_id`, `ledger_balance`, `collateral_value`, `intraday_mtm`, `usable_margin`, `used_margin`, `interest_charged`, `open_intraday_positions`

### `holdings_report.csv`

One row per client per symbol per EOD where quantity > 0.

Columns, in order: `date`, `client_id`, `symbol`, `quantity`, `pledged_qty`, `close_price`, `collateral_value`

Binding choices: `action` BUY, SELL, PLEDGE, UNPLEDGE, SQUARE_OFF, MTM_SQUARE_OFF; `decision` ACCEPTED or REJECTED; `rule_ids` your BR-nn ids separated by `;` (all failing rules, in check order); charges rounded to paisa per order; `net_amount` = trade_value + charges for buys, trade_value - charges for sells. Where two positions tie for 'largest' at an MTM square-off, close the one with the earlier opening order_id first.
