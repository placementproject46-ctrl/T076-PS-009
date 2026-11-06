# Centralized Input Validation Report: Stock Brokerage Engine

**Project ID:** T076 | **Problem Statement:** PS-009  
**Domain:** BFSI – Capital Markets | **Checkpoint:** Week 3 (6 November 2026)  
**Team Members:** Mohammed Saif (1AT24CG049), Mohanchandra M (1AT24CG050) | **Section:** CSD  

---

## 1. Architecture of Centralized Input Validation

In Week 3, input validation was centralized into a dedicated gateway service: `edu.atria.capstone.service.InputValidationService`. This layer guarantees that invalid, malformed, or out-of-schedule commands are rejected before invoking business domain logic or modifying database/ledger state.

```
Incoming Command String (e.g. "ORDER|ORD-001|2026-10-05 10:00:00|CL-01|INFY|BUY|DELIVERY|100|1500.00")
                                  |
                                  v
                +------------------------------------+
                |       InputValidationService       |
                |------------------------------------|
                | 1. Null / Empty check              |
                | 2. Command Verb recognition        |
                | 3. Token count validation          |
                | 4. Timestamp formatting (ISO-like) |
                | 5. Weekend / Holiday calendar check|
                | 6. Client ID format & existence    |
                | 7. Scrip Symbol format & existence |
                | 8. Numeric format (Int, BigDecimal)|
                +------------------------------------+
                      /                        \
           [Valid Result]                    [Invalid Result]
                 |                                  |
                 v                                  v
      Proceed to Command Handler            Reject early with rule IDs
      (BrokerageEngine execution)           and detailed error message
```

---

## 2. Validation Rules & Handlers

| Command Type | Expected Tokens | Validations Performed | Rule ID |
|:---|:---:|:---|:---:|
| **ORDER** | 9 | - `ORDER\|<ref_id>\|<timestamp>\|<client_id>\|<symbol>\|<side>\|<product>\|<qty>\|<price>`<br>- Timestamp format: `yyyy-MM-dd HH:mm:ss`<br>- Trading day verification (not holiday / not weekend)<br>- Client existence in `ClientRepository`<br>- Scrip existence in `ScripRepository`<br>- Side: `BUY` or `SELL`<br>- Product: `DELIVERY` or `INTRADAY`<br>- Quantity: Integer $> 0$<br>- Price: Decimal $> 0.00$ | `BR_VAL_001`<br>`BR_VAL_002`<br>`BR_VAL_003`<br>`BR_VAL_004`<br>`BR_VAL_010` |
| **PLEDGE** | 6 | - `PLEDGE\|<ref_id>\|<timestamp>\|<client_id>\|<symbol>\|<qty>`<br>- Trading day verification (no holiday pledging)<br>- Client existence<br>- Scrip existence & Group A/B eligibility<br>- Quantity $> 0$ | `BR_VAL_001`<br>`BR_VAL_002`<br>`BR_VAL_008`<br>`BR_VAL_010` |
| **UNPLEDGE** | 6 | - `UNPLEDGE\|<ref_id>\|<timestamp>\|<client_id>\|<symbol>\|<qty>`<br>- Trading day verification<br>- Client existence<br>- Quantity $> 0$ | `BR_VAL_001`<br>`BR_VAL_009`<br>`BR_VAL_010` |
| **FUNDS_IN** | 5 | - `FUNDS_IN\|<ref_id>\|<timestamp>\|<client_id>\|<amount>`<br>- Client existence<br>- Amount $> 0.00$ | `BR_VAL_001`<br>`BR_VAL_004` |
| **RETAG** | 4 | - `RETAG\|<ref_id>\|<symbol>\|<new_group>`<br>- Scrip existence<br>- Valid group: `GROUP_A`, `GROUP_B`, `GROUP_C` | `BR_VAL_002` |
| **PRICE_UPDATE** | 4 | - `PRICE_UPDATE\|<timestamp>\|<symbol>\|<price>`<br>- Scrip existence<br>- Price $> 0.00$ | `BR_VAL_002`<br>`BR_VAL_004` |
| **SETTLE** | 2 | - `SETTLE\|<date>`<br>- Date format: `yyyy-MM-dd` | Syntax |
| **INTRADAY_SQUARE_OFF** | 2 | - `INTRADAY_SQUARE_OFF\|<timestamp>` | Syntax |
| **EOD** | 2 | - `EOD\|<date>` | Syntax |
| **PRINT_REPORT** | 3 | - `PRINT_REPORT\|<client_id>\|<date>`<br>- Client existence | `BR_VAL_001` |

---

## 3. Test Coverage & Verification

Centralized input validation is thoroughly tested in `src/test/java/edu/atria/capstone/service/InputValidationTest.java`:

1. `testValidOrderCommandLinePassesValidation()`: Confirms that properly formed order lines pass validation without error.
2. `testOrderWithInsufficientTokensRejected()`: Rejects command lines with truncated or missing pipe tokens.
3. `testOrderWithInvalidTimestampRejected()`: Rejects unparseable date-time strings.
4. `testOrderWithUnknownClientIdRejected()`: Rejects orders for clients not registered in the system (`BR_VAL_001`).
5. `testOrderWithUnknownScripRejected()`: Rejects orders for unknown stock symbols (`BR_VAL_002`).
6. `testOrderOnHolidayRejected()`: Rejects orders submitted on official holidays like Gandhi Jayanti (`BR_VAL_010`).

All 6 automated tests pass with 0 failures.
