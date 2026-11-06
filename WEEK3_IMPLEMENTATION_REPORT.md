# Week 3 Implementation Report: Stock Brokerage Margin, Settlement and Pledge Engine

**Project ID:** T076  
**Problem Statement:** PS-009  
**Project Name:** Stock Brokerage Margin, Settlement and Pledge Engine  
**Domain:** BFSI – Capital Markets  
**Checkpoint Milestone:** Week 3 – 6 November 2026  
**Engineering Team:**  
1. Mohammed Saif – 1AT24CG049  
2. Mohanchandra M – 1AT24CG050  
**Section:** CSD  

---

## 1. Executive Summary

This report documents the architectural enhancement and business rule completion for the **Stock Brokerage Margin, Settlement and Pledge Engine (PS-009)**, transitioning the system from the Week 2 baseline to the complete Week 3 milestone.

The Week 3 delivery introduces production-grade settlement orchestration, real-time intraday risk management, demat collateral haircut tiering, negative ledger interest accrual, auction settlement for short delivery, and centralized input validation. The engine strictly adheres to regulatory capital market norms (SEBI / NSE / BSE) using double-entry ledger accounting, `BigDecimal` monetary precision with `RoundingMode.HALF_UP`, and reproducible CSV command-file automation.

---

## 2. Architecture & Subsystem Enhancements

```
+----------------------------------------------------------------------------------------------------+
|                                    BROKERAGE ENGINE (CLI / File)                                   |
+----------------------------------------------------------------------------------------------------+
       |                                      |                                     |
       v                                      v                                     v
+-----------------------+           +-----------------------+             +-----------------------+
| InputValidationService|           | OrderValidationService|             | OrderExecutionService |
| - Token count checks  |           | - Precedence:         |             | - Delivery routing    |
| - Timestamp formats   |           |   1. Holdings         |             | - Intraday routing    |
| - Holiday/weekend     |           |   2. Margin (50:50)   |             | - Contract note gen   |
| - Numeric bounds      |           |   3. Price band (+-10%)             | - Ledger accounting   |
+-----------------------+           +-----------------------+             +-----------------------+
                                              |                                     |
                      +-----------------------+-----------------------+             |
                      |                       |                       |             |
                      v                       v                       v             v
            +--------------------+  +--------------------+  +--------------------+  |
            |   MarginService    |  |  SettlementService |  |   IntradayService  |<-+
            | - Delivery (100%)  |  | - T+1 queue        |  | - Short selling    |  |
            | - Intraday (20%)   |  | - 80% same-day cred|  | - Real-time MTM    |  |
            | - Haircuts (10/25) |  | - Holiday rolling  |  | - 80% margin call  |  |
            | - 50:50 Collateral |  | - Stock delivery   |  | - 15:20 square-off |  |
            +--------------------+  +--------------------+  +--------------------+  |
                      |                       |                       |             |
                      v                       v                       v             v
            +-----------------------------------------------------------------------------+
            |                        REPOSITORIES & LEDGER (Double Entry)                 |
            | ClientRepository | ScripRepository | StockPriceRepository | OrderRepository |
            | HolidayRepository | LedgerRepository (Daily 0.05% Interest Calculation)    |
            +-----------------------------------------------------------------------------+
```

### Key Subsystems:
1. **Settlement Engine (`SettlementService`)**:
   - Manages the T+1 rolling settlement queue, dynamically skipping weekends and gazetted market holidays (`HolidayRepository`).
   - Implements the regulatory **80% same-day credit rule**: 80% of net delivery sale proceeds are instantly unlocked on trade date $T$ for purchasing new positions, while the remaining 20% remains locked until pay-in on $T+1$.
   - Enforces the free holding formula: $FreeQty = TotalQty - PledgedQty - SoldTodayQty$.

2. **Intraday Risk Engine (`IntradayService`)**:
   - Supports long and short intraday trading (`ProductType.INTRADAY`) without requiring upfront physical stock borrow.
   - Calculates real-time Mark-to-Market (MTM) gains and losses on every `PRICE_UPDATE`.
   - **Margin Call Breach**: Automatically squares off the largest open position when available equity drops below 80% of required margin.
   - **Tie-Breaker Logic (EC-08)**: When two positions tie for largest market value, deterministically squares off the position opened earlier (`openingOrderId` / earlier timestamp).
   - **15:20 Square-Off (EC-03)**: Automatically squares off all open intraday positions at 15:20:00 with a ₹50 penalty debited to the ledger.

3. **Collateral & Haircut Management (`MarginService`, `PledgeGroup`)**:
   - Strict haircut tiers:
     - Group A: 10% haircut (collateral factor = 0.90)
     - Group B: 25% haircut (collateral factor = 0.75)
     - Group C: 100% haircut (collateral factor = 0.00 / non-pledgeable)
   - Dynamic revaluation: Re-tagging scrips via `RETAG` or market price changes dynamically adjusts collateral value in real time.
   - **50:50 Cash-Collateral Rule (EC-02)**: Collateral can satisfy at most 50% of the required margin; the remaining 50% must be backed by cash.

4. **Negative Ledger Interest Engine (`InterestCalculationService`)**:
   - Calculates daily simple interest at 0.05% per day on overnight debit balances (`LedgerBalance < 0`).
   - Interest calculation commences from the day after the account first went negative.

5. **Auction Settlement Engine (`AuctionSettlementService`)**:
   - Resolves short delivery defaults by auctioning the defaulted quantity at current market closing price plus a 20% penalty.

6. **Centralized Input Validation (`InputValidationService`)**:
   - Intercepts all incoming command lines before execution, verifying token counts, data types, timestamp formats, client existence, scrip symbols, and market holidays.

---

## 3. Verification & Test Metrics

The Week 3 test suite includes **93 automated unit, edge case, and end-to-end integration tests** executed with JUnit 5:

| Test Class | Category | Tests Run | Failures | Status |
|:---|:---|:---:|:---:|:---:|
| `ClientServiceTest` | Client Management | 4 | 0 | PASSED |
| `OrderValidationTest` | Business Validation Rules | 5 | 0 | PASSED |
| `MarginCalculationTest` | Margin & 50:50 Rules | 4 | 0 | PASSED |
| `BrokerageCalculationTest` | Brokerage Formulae | 4 | 0 | PASSED |
| `StatutoryChargesTest` | Regulatory Charges & GST | 7 | 0 | PASSED |
| `SettlementServiceTest` | T+1 Queue & 80% Usability | 4 | 0 | PASSED |
| `IntradayServiceTest` | Intraday MTM & Square-Off | 3 | 0 | PASSED |
| `PledgeServiceTest` | Pledging & Haircuts | 4 | 0 | PASSED |
| `InterestCalculationTest` | Negative Ledger Interest | 3 | 0 | PASSED |
| `PriceBandValidationTest` | Operating Band Checks | 4 | 0 | PASSED |
| `AuctionSettlementTest` | Short Delivery Auction | 1 | 0 | PASSED |
| `InputValidationTest` | Command Syntax & Holidays | 6 | 0 | PASSED |
| `EdgeCaseTest` | Planted Edge Cases (EC-01 to EC-08) | 8 | 0 | PASSED |
| `EndToEndTest` | Full Command Stream Simulation | 2 | 0 | PASSED |
| *Regression Suites (Charge, Client, Margin, Exec, OrderVal)* | Regression & Invariants | 34 | 0 | PASSED |
| **Total** | **All Test Classes** | **93** | **0** | **100% PASS** |

---

## 4. Evaluation Deliverables Generated

Executing the official command file (`sample-data/T076-PS-009-sample-data/commands.txt`) generates all 4 required evaluation CSV reports in `output/`:
1. `output/order_decisions.csv` (11.7 KB, 86 rows)
2. `output/contract_notes.csv` (8.7 KB, 47 rows)
3. `output/eod_report.csv` (7.4 KB, 133 rows)
4. `output/holdings_report.csv` (1.5 KB, 29 rows)
