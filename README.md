# PS-009: Stock Brokerage Margin, Settlement and Pledge Engine
## Capstone Project – Milestone: Week 3 Complete Implementation

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://adoptium.net/)
[![Maven 3.9](https://img.shields.io/badge/Build-Maven%203.9-blue.svg)](https://maven.apache.org/)
[![JUnit 5](https://img.shields.io/badge/Tests-JUnit%205%20(93%20Passing)-green.svg)](https://junit.org/junit5/)
[![Domain](https://img.shields.io/badge/Domain-BFSI%20Capital%20Markets-purple.svg)]()

---

### Capstone Project Metadata

- **Project Name:** Stock Brokerage Margin, Settlement and Pledge Engine
- **Problem Statement ID:** PS-009
- **Team ID:** T076
- **Domain:** BFSI – Capital Markets
- **Academic Branch:** Computer Science and Design (CSD)
- **Checkpoint Milestone:** Week 3 – 6 November 2026

#### Engineering Team Members:
1. **Mohammed Saif** – `1AT24CG049`
2. **Mohanchandra M** – `1AT24CG050`

---

## 1. Project Overview & Objective

The **Stock Brokerage Margin, Settlement and Pledge Engine** is an enterprise-grade, command-file-driven core backend system simulating the end-to-end operational lifecycle of a modern Indian capital markets brokerage house (comparable to Zerodha, Groww, AngelOne).

The engine handles client onboarding and ledgers, stock price master data, order validation against strict regulatory price bands and margin rules, statutory charge computation, ledger accounting, delivery holdings management with weighted average cost calculation, pledge collateral management with dynamic haircut revaluation, **T+1 rolling delivery settlement with 80% same-day sell proceeds usability**, **intraday short selling with real-time MTM evaluation and margin-call auto square-off**, **15:20 auto square-off with penalty**, **negative ledger interest accrual (0.05% daily)**, and **auction settlement for short delivery**.

### Key Week 3 Deliverables:
1. **Complete Business Rules & Subsystems:** All business rules from PS-009 implemented and verified.
2. **Automated Edge Case Test Suite:** Automated JUnit 5 tests covering all 8 planted edge cases (`EC-01` to `EC-08`).
3. **14 Dedicated Test Suites:** 93 automated tests across 14 test classes with 100% pass rate.
4. **End-to-End Command Simulation:** Full processing of `commands.txt` producing evaluation CSV reports in `output/`:
   - `order_decisions.csv`
   - `contract_notes.csv`
   - `eod_report.csv`
   - `holdings_report.csv`
5. **Comprehensive Technical Documentation:**
   - `WEEK3_IMPLEMENTATION_REPORT.md`
   - `WEEK3_GAP_ANALYSIS.md`
   - `BUSINESS_RULE_TRACEABILITY.md`
   - `EDGE_CASE_TEST_REPORT.md`
   - `INPUT_VALIDATION_REPORT.md`

---

## 2. System Architecture

The engine is engineered according to Clean Architecture and Domain-Driven Design (DDD) principles:

```
+----------------------------------------------------------------------------------------------------+
|                                Command Line Interface / Main Runner                                |
|           [--demo] Happy Path Demo  |  [--commands <file>] Batch  |  [--interactive] Shell         |
+----------------------------------------------------------------------------------------------------+
                                                  |
                                                  v
+----------------------------------------------------------------------------------------------------+
|                                          BrokerageEngine                                           |
+----------------------------------------------------------------------------------------------------+
         |                                       |                                      |
         v                                       v                                      v
+------------------------+             +------------------------+            +-----------------------+
| InputValidationService |             | OrderValidationService |            | OrderExecutionService |
| - Syntax & token count |             | - Strict Precedence:   |            | - Delivery routing    |
| - Timestamp parsing    |             |   1. Holdings check    |            | - Intraday routing    |
| - Market holiday check |             |   2. Margin (50:50)    |            | - Contract notes      |
| - Symbol & Client check|             |   3. Price band (+-10%)|            | - Double-entry ledger |
+------------------------+             +------------------------+            +-----------------------+
                                                 |                                      |
                      +--------------------------+-----------------------+              |
                      |                          |                       |              |
                      v                          v                       v              v
            +--------------------+     +--------------------+  +--------------------+   |
            |   MarginService    |     |  SettlementService |  |   IntradayService  |<--+
            | - Delivery (100%)  |     | - T+1 queue        |  | - Short selling    |   |
            | - Intraday (20%)   |     | - 80% same-day cred|  | - Real-time MTM    |   |
            | - Haircuts (10/25) |     | - Holiday rolling  |  | - 80% margin call  |   |
            | - 50:50 Collateral |     | - Demat delivery   |  | - 15:20 square-off |   |
            +--------------------+     +--------------------+  +--------------------+   |
                      |                          |                       |              |
                      v                          v                       v              v
+----------------------------------------------------------------------------------------------------+
|                                    REPOSITORIES & LEDGER (Double Entry)                            |
|  ClientRepository  |  ScripRepository  |  StockPriceRepository  |  HolidayRepository  |  OrderRepo |
|  LedgerRepository (Immutable journal, Daily 0.05% negative interest calculation)                  |
+----------------------------------------------------------------------------------------------------+
```

### Module Responsibilities:
- **`edu.atria.capstone.model`:** Immutable and domain-rich entities (`Client`, `Holding`, `Scrip`, `StockPrice`, `Order`, `ContractNote`, `LedgerEntry`, `ChargesBreakdown`, `ValidationResult`, `SettlementObligation`, `IntradayPosition`, `AuctionRecord`, `EodRecord`).
- **`edu.atria.capstone.repository`:** In-memory high-speed data stores (`ClientRepository`, `ScripRepository`, `StockPriceRepository`, `HolidayRepository`, `OrderRepository`, `LedgerRepository`).
- **`edu.atria.capstone.service`:**
  - `ChargeCalculationService`: Modular calculation of Brokerage, STT, Exchange Turnover, SEBI turnover fees, Stamp Duty, and GST.
  - `MarginService`: Delivery (100%) and Intraday (20%) initial margin calculations, collateral valuation with haircut groups, and SEBI 50:50 cash-collateral margin rules.
  - `SettlementService`: T+1 settlement queue, holiday/weekend calendar exclusion, 80% same-day delivery sale proceeds credit, and free holding formula ($Free = Total - Pledged - SoldToday$).
  - `IntradayService`: Intraday position tracking, short selling, real-time MTM calculation, 80% margin-call breach auto square-off with tie-breaker, and 15:20 square-off with ₹50 penalty.
  - `InterestCalculationService`: 0.05% daily negative ledger balance interest accrual.
  - `AuctionSettlementService`: Short delivery auction resolution at closing price + 20% penalty.
  - `InputValidationService`: Centralized command gateway validating formats, numerics, and holidays.
  - `OrderValidationService`: Evaluates all business rules simultaneously without early termination following strict precedence.
  - `OrderExecutionService`: Executes valid trades, debits/credits ledgers, updates holdings, and guarantees account isolation on rejection.
  - `ReportService`: Generates CSV evaluation outputs (`order_decisions.csv`, `contract_notes.csv`, `eod_report.csv`, `holdings_report.csv`) and terminal account statements.
- **`edu.atria.capstone.loader`:** CSV stream parser loading `clients.csv`, `scrips.csv`, `holdings_opening.csv`, `prices.csv`, and `holidays.csv`.
- **`edu.atria.capstone.engine`:** Command stream interpreter and Happy-Path orchestrator.

---

## 3. Business Rules Specification

| Business Rule ID | Name | Description & Evaluation Logic |
|---|---|---|
| **`BR_VAL_001`** | Client Existence | Client ID must be registered in the brokerage client master. |
| **`BR_VAL_002`** | Scrip Master Validity | Stock symbol must exist in the scrip master list. |
| **`BR_VAL_003`** | Positive Quantity | Order quantity must be a positive integer ($Quantity > 0$). |
| **`BR_VAL_004`** | Positive Price | Order price must be greater than zero ($Price > 0.00$). |
| **`BR_VAL_005`** | Available Margin / Funds | Available margin must satisfy required margin: 100% of trade value for Delivery Buy, 20% for Intraday. Collateral capped at 50% for Intraday. |
| **`BR_VAL_006`** | Free Holdings Check | Delivery Sell requires: $Free Shares = Quantity - Pledged - SoldToday \ge Order Quantity$. |
| **`BR_VAL_007`** | Operating Price Band | Order price must fall strictly within $\pm 10.00\%$ of previous day close ($0.90 \times P_{close} \le P_{order} \le 1.10 \times P_{close}$). |
| **`BR_VAL_008`** | Pledge Collateral Eligibility | Only Group A (10% haircut) and Group B (25% haircut) scrips are pledgeable. Group C scrips are non-pledgeable (100% haircut). |
| **`BR_VAL_009`** | Unpledge Quantity Limit | Unpledge quantity cannot exceed currently pledged shares. |
| **`BR_VAL_010`** | Market Holiday Guard | Commands on market holidays and weekends are rejected. |

> **Evaluation Precedence Fulfilled:** 1. Holdings check -> 2. Margin check -> 3. Price-band check -> 4. Execution decision. Validations collect all applicable rule failures rather than stopping on first error.

---

## 4. Statutory & Brokerage Charges Engine

All calculations are executed strictly using Java `BigDecimal` with `RoundingMode.HALF_UP` (2 decimal places):

| Charge Component | Delivery BUY | Delivery SELL | Intraday BUY | Intraday SELL | Rounding |
|---|---|---|---|---|---|
| **Brokerage** | $\min(₹20, 0.1\% \times V)$ | $\min(₹20, 0.1\% \times V)$ | $\min(₹20, 0.05\% \times V)$ | $\min(₹20, 0.05\% \times V)$ | Scale 2, HALF_UP |
| **Securities Transaction Tax (STT)** | $0.1\% \times V$ | $0.1\% \times V$ | $0.0\%$ | $0.025\% \times V$ | Nearest Rupee |
| **Exchange Transaction Fee** | $0.00345\% \times V$ | $0.00345\% \times V$ | $0.00345\% \times V$ | $0.00345\% \times V$ | Scale 2, HALF_UP |
| **SEBI Turnover Fee** | $0.0001\% \times V$ | $0.0001\% \times V$ | $0.0001\% \times V$ | $0.0001\% \times V$ | Scale 2, HALF_UP |
| **Stamp Duty** | $0.015\% \times V$ | $0.0\%$ | $0.003\% \times V$ | $0.0\%$ | Scale 2, HALF_UP |
| **GST** | $18\% \times (Brok + Exch)$ | $18\% \times (Brok + Exch)$ | $18\% \times (Brok + Exch)$ | $18\% \times (Brok + Exch)$ | Scale 2, HALF_UP |

---

## 5. Verified Edge Cases (EC-01 to EC-08)

All 8 planted edge cases are fully verified in `EdgeCaseTest.java`:

- **EC-01 (Friday Sell Proceeds):** 80% of delivery sell proceeds are instantly available on Friday for new buys; 20% locked until Monday T+1 settlement.
- **EC-02 (50:50 Cash-Collateral Rule):** Order requiring ₹1,00,000 margin with ₹40,000 cash and ₹90,000 collateral is rejected because cash cannot cover the mandatory 50% component.
- **EC-03 (15:20 Auto Square-Off):** Intraday position entered at 15:19:59 is automatically closed at 15:20:00 with ₹50 penalty debited.
- **EC-04 (Dynamic Collateral Haircut):** Stock re-tagged from Group A (10% haircut) to Group C (100% haircut) immediately drops collateral value to ₹0.00.
- **EC-05 (Price Band Boundaries):** Orders at exactly -10% and +10% of previous close are valid; orders at -10.01% and +10.01% are rejected with `BR_VAL_PRICE_BAND`.
- **EC-06 (Negative Ledger Balance):** Account at negative balance incurs 0.05% daily simple interest from the next day.
- **EC-07 (Market Holiday Guard):** PLEDGE or ORDER command on market holidays is intercepted and rejected.
- **EC-08 (MTM Square-Off Tie-Breaker):** Two open intraday positions with tied market values breach the 80% equity threshold; the position with lower `openingOrderId` is closed first.

---

## 6. How to Build, Test and Run

### Prerequisites
- JDK 17 or higher (tested on OpenJDK 21 LTS)
- Apache Maven 3.8+

### Build and Run Tests
```powershell
# Compile and run all 93 unit, edge case, and integration tests
mvn clean test
```

### Run Full Command Stream Simulation
```powershell
# Run engine against official sample-data commands.txt
java -jar target/stock-brokerage-engine-1.0.0.jar --commands sample-data/T076-PS-009-sample-data/commands.txt
```

### Run Happy-Path Demonstration
```powershell
java -jar target/stock-brokerage-engine-1.0.0.jar --demo
```

### Run Interactive Console
```powershell
java -jar target/stock-brokerage-engine-1.0.0.jar --interactive
```

---

## 7. Evaluation Output Files

Executing `--commands sample-data/T076-PS-009-sample-data/commands.txt` generates:

1. `output/order_decisions.csv`: Decision log for every order with rule IDs, quantities, prices, margins, and rejection reasons.
2. `output/contract_notes.csv`: Full breakdown of executed trades including Brokerage, STT, Exchange fees, SEBI charges, Stamp duty, GST, and penalties.
3. `output/eod_report.csv`: End-of-day balances, collateral values, intraday MTM, usable margins, and overnight interest.
4. `output/holdings_report.csv`: Demat portfolio holdings, total/free/pledged quantities, LTP, and market valuations.
