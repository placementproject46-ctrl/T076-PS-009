# Week 3 Gap Analysis: PS-009 Stock Brokerage Margin, Settlement & Pledge Engine
**Team ID:** T076 | **Checkpoint:** Week 3 (6 November 2026)  
**Domain:** BFSI – Capital Markets | **Language:** Java 17+ (Java 21 LTS)

---

## 1. Executive Summary

This gap analysis reviews the existing Week 2 implementation of the Stock Brokerage Margin, Settlement and Pledge Engine against the full functional requirements, business rules, edge cases, and architectural standards demanded for the **Week 3 Checkpoint**.

The existing Week 2 project provided a foundation: domain models, modular statutory charge computation, price-band checks, and a happy-path demonstration. However, Week 3 requires enterprise-grade lifecycle handling: T+1 delivery settlement scheduling, intraday position tracking with real-time MTM margin health monitoring, auto square-offs with penalties, negative balance interest accrual, short delivery auction mechanisms, centralized input validation, and verification of all eight planted edge cases (EC-01 through EC-08).

---

## 2. Existing Functionality (Week 2 Baseline)

The following components are currently implemented and verified:
1. **Client & Account Master (`Client`, `ClientRepository`)**:
   - Client registration, unique client IDs, cash balance maintenance, holding master.
2. **Scrip & Price Master (`Scrip`, `StockPrice`, `StockPriceRepository`)**:
   - Stock symbol, haircut pledge group, lot sizes, closing prices, and previous closing price retrieval.
3. **Statutory & Brokerage Charges Engine (`ChargeCalculationService`)**:
   - Modular methods for Delivery Brokerage (₹0), Intraday Brokerage (min ₹1, cap ₹20, 0.03%), Delivery STT (0.1%), Intraday STT (0.025% on sell), Exchange transaction fee (0.00345%), SEBI fee (₹10/cr), Stamp Duty (0.015% delivery buy, 0.003% intraday buy), GST (18%), and rounding to 2 decimal places (`RoundingMode.HALF_UP`).
4. **Initial Margin Calculations (`MarginService`)**:
   - Delivery Margin (100% of trade value), Intraday Margin (20% of trade value), basic collateral valuation.
5. **Basic Order Validation (`OrderValidationService`)**:
   - Validates client existence (`BR_VAL_001`), symbol validity (`BR_VAL_002`), positive quantity (`BR_VAL_003`), positive price (`BR_VAL_004`), available funds (`BR_VAL_005`), free holdings (`BR_VAL_006`), operating price band $\pm 10\%$ (`BR_VAL_007`), and Group C pledge rejection (`BR_VAL_008`).
   - Evaluates all applicable validations simultaneously without early exit.
6. **Command Processing**:
   - Basic line-by-line parsing of `commands.txt` and generation of `order_decisions.csv`, `contract_notes.csv`, `holdings_report.csv`.
7. **Test Suite**:
   - 34 passing tests covering charge calculations, basic margin, basic validation, and client management.

---

## 3. Identified Gaps & Missing Functionality

### 3.1 Gap A: T+1 Settlement Lifecycle & Intraday Proceeds Usability (EC-01)
- **Current Behavior**:
  - Delivery BUY and SELL orders modify client ledger cash and holdings immediately upon order acceptance.
- **Required Week 3 Behavior**:
  - **Trade Date $T$**:
    - Delivery BUY: Checks available margin/cash on $T$; blocks required funds; actual cash debit and demat stock delivery credit occur at 08:00 on settlement date $T+1$.
    - Delivery SELL: Blocks sold shares immediately on $T$ ($Free = Total - Pledged - SoldToday$).
    - **80% Immediate Usability Rule (EC-01)**: Exactly 80% of gross delivery sale proceeds become available on $T$ for new purchases. The remaining 20% becomes available only on $T+1$ after settlement.
  - **Settlement Date $T+1$ (`SETTLE|date` at 08:00)**:
    - Settle all delivery BUY obligations due on this date: debit ledger cash, credit demat holding.
    - Settle all delivery SELL obligations due on this date: credit 100% net sale proceeds to ledger, permanently remove shares from demat.
    - Settlement date calculation must exclude weekends (Saturday, Sunday) and official market holidays listed in `holidays.csv`.

### 3.2 Gap B: Intraday Short Selling, Open Position Tracking & Real-Time MTM (EC-03, EC-08)
- **Current Behavior**:
  - Intraday orders only deduct charges and do not track open long/short positions, net quantity, average entry price, or intraday MTM profit/loss.
- **Required Week 3 Behavior**:
  - Support Intraday Short Selling: allow SELL orders without holding settled shares, backed by 20% intraday margin.
  - Position State Engine: track open intraday positions per client per symbol (`netQuantity`, `avgCost`, `openingOrderId`, `timestamp`).
  - **Real-Time MTM (`PRICE_UPDATE|timestamp|symbol|price`)**:
    - Recalculate MTM P&L for every client with an open intraday position:
      $$\text{Long Position: } MTM = Qty \times (LTP - AvgCost)$$
      $$\text{Short Position: } MTM = |Qty| \times (AvgCost - LTP)$$
    - Monitor Margin Health:
      $$\text{Available Margin} = \text{Ledger Cash} + \text{Eligible Collateral} + \text{Intraday MTM P\&L}$$
      $$\text{Threshold} = 80\% \times \text{Required Margin}$$
    - If $\text{Available Margin} < 80\% \times \text{Required Margin}$, trigger **MTM Auto Square-Off**:
      - Square off largest position first (value = $|Qty| \times LTP$).
      - **Tie-Breaker Rule (EC-08)**: If two positions tie for largest value, square off the position opened earlier (lower `order_id` / earlier timestamp) first.
      - Record decision as `MTM_SQUARE_OFF` with reference ID `SQ-<client_id>-<symbol>-<date>`.
  - **End-of-Day Square-Off at 15:20 (`INTRADAY_SQUARE_OFF|timestamp`)**:
    - Automatically square off all remaining open intraday positions at the day's `close_price`.
    - Apply statutory ₹50 penalty per squared-off position.
    - Record decision as `SQUARE_OFF` with reference ID `SQ-<client_id>-<symbol>-<date>`.
    - Post realized P&L minus charges and ₹50 penalty to client ledger.

### 3.3 Gap C: Haircut Rates & Collateral Management (EC-02, EC-04, EC-07)
- **Current Behavior**:
  - GROUP_B haircut was set to 20%; prompt explicitly specifies **GROUP_B haircut: 25%**.
  - PLEDGE requests do not check if the timestamp falls on an official market holiday.
- **Required Week 3 Behavior**:
  - Update `PledgeGroup.GROUP_B` haircut to 25% (collateral factor = 0.75).
  - Verify exact 50% collateral cap rule ($Collateral \le 50\% \text{ of Required Margin}$, cash $\ge 50\%$).
  - **Dynamic Retagging (EC-04)**: When a stock is re-tagged from GROUP_A to GROUP_C, its collateral factor drops immediately to 0.00; existing pledges are preserved in quantity but provide nil collateral value.
  - **Holiday Check (EC-07)**: Reject or validate pledge requests timestamped on market holidays (`holidays.csv`).
  - Prevent unpledge if releasing collateral causes available margin to drop below required margin for active open positions.

### 3.4 Gap D: Negative Ledger Balance Interest (EC-06)
- **Current Behavior**:
  - No interest calculation logic on negative ledger cash balances.
- **Required Week 3 Behavior**:
  - During `EOD|date` processing:
    - Apply $0.05\%$ daily simple interest ($18.25\%$ p.a.) on negative ledger balances.
    - Interest starts from the *next day* after the balance turns negative.
    - Continue charging interest daily until the negative balance is cleared by incoming funds or settled credits.
    - Verified with negative balance of exactly ₹1.00 (EC-06).

### 3.5 Gap E: Short Delivery Auction Settlement (Phase 2.J)
- **Current Behavior**:
  - Short delivery auction mechanism is not yet implemented.
- **Required Week 3 Behavior**:
  - Identify delivery shortfalls when an obligation cannot be met on settlement day.
  - Execute auction settlement at closing price plus 20% penalty on shortfall value.
  - Debit defaulting seller, credit buyer/exchange, and update ledger and reports.

### 3.6 Gap F: Centralized Input Validation Layer (Phase 3)
- **Current Behavior**:
  - Validations are distributed and assume well-formed CSV rows and commands.
- **Required Week 3 Behavior**:
  - Create `InputValidationService`:
    - CSV structural validation (missing headers, missing columns, malformed delimiters, empty files).
    - Field-level validation (invalid PAN, invalid 10-digit mobile, duplicate client IDs, duplicate scrips).
    - Command format validation (unrecognized command names, invalid timestamps, missing pipe tokens, non-numeric values).
    - Safe error handling: log meaningful error with rule/validation ID, continue processing without state corruption.

### 3.7 Gap G: Comprehensive Evaluation Reports (Phase 6)
- **Current Behavior**:
  - Output CSVs generated for `order_decisions.csv`, `contract_notes.csv`, `holdings_report.csv`.
- **Required Week 3 Behavior**:
  - Add complete generation of `eod_report.csv`:
    - Columns: `date,client_id,ledger_balance,collateral_value,intraday_mtm,usable_margin,used_margin,interest_charged,open_intraday_positions`
  - Ensure exact column order, header casing, decimal formatting, and sorting match official README specifications.

### 3.8 Gap H: Dedicated 14 Test Classes (Phase 5)
- **Current Behavior**:
  - 5 test classes.
- **Required Week 3 Behavior**:
  - Implement all 14 requested JUnit 5 test classes:
    1. `ClientServiceTest`
    2. `OrderValidationTest`
    3. `MarginCalculationTest`
    4. `BrokerageCalculationTest`
    5. `StatutoryChargesTest`
    6. `SettlementServiceTest`
    7. `IntradayServiceTest`
    8. `PledgeServiceTest`
    9. `InterestCalculationTest`
    10. `PriceBandValidationTest`
    11. `AuctionSettlementTest`
    12. `InputValidationTest`
    13. `EdgeCaseTest` (EC-01 to EC-08)
    14. `EndToEndTest`

---

## 4. Files Requiring Creation or Modification

| Component | Target File | Nature of Changes |
|---|---|---|
| **Pledge Model** | `model/PledgeGroup.java` | Update GROUP_B haircut to 25% (factor 0.75). |
| **Intraday Position Model** | `model/IntradayPosition.java` | Create new model tracking symbol, net qty, avg cost, opening order ID, and MTM. |
| **Settlement Obligation Model** | `model/SettlementObligation.java` | Create new model tracking trade date, settlement date, client, symbol, side, qty, net amount. |
| **EOD Snapshot Model** | `model/EodRecord.java` | Create new model for `eod_report.csv` columns. |
| **Auction Model** | `model/AuctionRecord.java` | Create model for short delivery auction records. |
| **Validation Service** | `service/InputValidationService.java` | Create centralized validation service for CSVs, files, and command lines. |
| **Settlement Service** | `service/SettlementService.java` | Create dedicated T+1 settlement engine, 80% trade-date sale proceeds usability, and holiday-aware date calculator. |
| **Intraday Service** | `service/IntradayService.java` | Create intraday position manager, real-time MTM evaluator, breach detector, auto square-off, tie-breaker, and ₹50 penalty handler. |
| **Interest Service** | `service/InterestCalculationService.java` | Create daily 0.05% negative ledger balance simple interest calculation engine. |
| **Auction Service** | `service/AuctionSettlementService.java` | Create short delivery detection, closing price + 20% penalty auction settlement engine. |
| **Margin Service** | `service/MarginService.java` | Integrate 80% unsettled delivery sale credit, intraday MTM adjustments, and 25% Group B haircut. |
| **Validation Service** | `service/OrderValidationService.java` | Implement order-processing precedence: 1. Holdings check, 2. Margin check, 3. Price band check, 4. Execution decision. Add holiday validation (`BR_VAL_010`). |
| **Execution Service** | `service/OrderExecutionService.java` | Route delivery orders to T+1 settlement queue; route intraday orders to IntradayService; maintain separate free holdings. |
| **Report Service** | `service/ReportService.java` | Add complete `eod_report.csv` generation; support auto square-off entries in `order_decisions.csv` and `contract_notes.csv`. |
| **Engine Orchestrator** | `engine/BrokerageEngine.java` | Wire SETTLE, INTRADAY_SQUARE_OFF, PRICE_UPDATE, RETAG, EOD, and PRINT_REPORT into new domain services. |
| **Test Suite** | `src/test/java/...` | Implement all 14 designated JUnit 5 test classes. |

---

## 5. Detailed Implementation Plan

```
+-----------------------------------------------------------------------------------------+
| STEP 1: Core Domain Models & Haircuts Update                                            |
|   - Update PledgeGroup (GROUP_B = 25%)                                                  |
|   - Add IntradayPosition, SettlementObligation, EodRecord, AuctionRecord                |
+-----------------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
| STEP 2: Dedicated Specialized Engines                                                   |
|   - SettlementService: T+1 queue, 80% same-day credit, holiday/weekend exclusion        |
|   - IntradayService: Position tracking, MTM evaluation, 80% margin call, 15:20 close    |
|   - InterestCalculationService: 0.05% daily negative balance simple interest             |
|   - AuctionSettlementService: Short delivery penalty (20% + closing price)              |
|   - InputValidationService: Centralized file, CSV and command syntax validator          |
+-----------------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
| STEP 3: Order Validation Precedence & Integration                                       |
|   - Precedence: 1. Holdings check -> 2. Margin check -> 3. Price band -> 4. Decision     |
|   - Wire BrokerageEngine with SETTLE, PRICE_UPDATE, SQUARE_OFF, EOD, RETAG handlers     |
+-----------------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
| STEP 4: Reporting & Output Generation                                                   |
|   - Generate order_decisions.csv, contract_notes.csv, eod_report.csv, holdings_report.csv|
+-----------------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
| STEP 5: Verification & Comprehensive JUnit 5 Test Suite (14 Test Classes)               |
|   - ClientServiceTest, OrderValidationTest, MarginCalculationTest, etc.                 |
|   - Automated verification of EC-01 through EC-08                                       |
|   - Execute official commands.txt and inspect all output CSVs field-by-field            |
+-----------------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
| STEP 6: Documentation Deliverables (Phase 8)                                            |
|   - WEEK3_IMPLEMENTATION_REPORT.md, BUSINESS_RULE_TRACEABILITY.md,                     |
|     EDGE_CASE_TEST_REPORT.md, INPUT_VALIDATION_REPORT.md, updated README.md              |
+-----------------------------------------------------------------------------------------+
```
