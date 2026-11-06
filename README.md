# PS-009: Stock Brokerage Margin, Settlement and Pledge Engine
## Capstone Project – Milestone: Week 2 Core Flows

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://adoptium.net/)
[![Maven 3.9](https://img.shields.io/badge/Build-Maven%203.9-blue.svg)](https://maven.apache.org/)
[![JUnit 5](https://img.shields.io/badge/Tests-JUnit%205%20(34%20Passing)-green.svg)](https://junit.org/junit5/)
[![Domain](https://img.shields.io/badge/Domain-BFSI%20Capital%20Markets-purple.svg)]()

---

### Capstone Project Metadata

- **Project Name:** Stock Brokerage Margin, Settlement and Pledge Engine
- **Problem Statement ID:** PS-009
- **Team ID:** T076
- **Domain:** BFSI – Capital Markets
- **Academic Branch:** Computer Science and Design (CSD)
- **Milestone:** Week 2 – Core Flows

#### Team Members:
1. **Mohammed Saif** – `1AT24CG049`
2. **Mohanchandra M** – `1AT24CG050`

---

## 1. Project Overview & Objective

The **Stock Brokerage Margin, Settlement and Pledge Engine** is a high-performance, command-driven core backend system simulating the operational lifecycle of a modern Indian capital markets brokerage house (comparable to Zerodha, Groww, AngelOne).

The engine handles client onboarding and ledgers, stock price master data, order validation against strict regulatory price bands and margin rules, statutory charge computation, ledger accounting, delivery holdings management with weighted average cost calculation, and pledge collateral management.

### Key Week 2 Deliverables:
1. **Clean Git Repository:** Modular architecture with clean git commit history ready for mentor review.
2. **Happy-Path Demonstration:** 4-step core demonstration flow executing on real sample data.
3. **Comprehensive Unit Test Suite:** 34 JUnit 5 unit tests verifying all business rules, statutory charges, price bands, and ledger balance guarantees.

---

## 2. System Architecture

The engine is engineered according to Clean Architecture and Domain-Driven Design (DDD) principles:

```
+-------------------------------------------------------------------------------+
|                       Command Line Interface / Main Runner                   |
|       [--demo] Happy Path Demo  |  [--commands] Batch File  |  [--interactive]|
+-------------------------------------------------------------------------------+
                                      |
                                      v
+-------------------------------------------------------------------------------+
|                               BrokerageEngine                                 |
+-------------------------------------------------------------------------------+
    |                  |                      |                     |
    v                  v                      v                     v
+-------------+ +--------------------+ +-------------------+ +------------------+
| Client Repo | | Stock & Price Repo | | Order Validation  | | Order Execution  |
|  & Ledgers  | |  & Haircut Groups  | |  Engine (BR_VAL)  | |  & Settlements   |
+-------------+ +--------------------+ +-------------------+ +------------------+
                                              |                     |
                                              v                     v
                                       +-------------------+ +------------------+
                                       |   Margin Engine   | | Statutory Charge |
                                       | (100% / 20% / 50%)| |  Engine (SEBI)   |
                                       +-------------------+ +------------------+
```

### Module Responsibilities:
- **`edu.atria.capstone.model`:** Immutable and domain-rich entities (`Client`, `Holding`, `Scrip`, `StockPrice`, `Order`, `ContractNote`, `LedgerEntry`, `ChargesBreakdown`, `ValidationResult`).
- **`edu.atria.capstone.repository`:** In-memory high-speed data stores (`ClientRepository`, `ScripRepository`, `StockPriceRepository`, `HolidayRepository`, `OrderRepository`, `LedgerRepository`).
- **`edu.atria.capstone.service`:**
  - `ChargeCalculationService`: Modular calculation of Brokerage, STT, Exchange Turnover, SEBI turnover fees, Stamp Duty, and GST.
  - `MarginService`: Delivery (100%) and Intraday (20%) initial margin calculations, collateral valuation with haircut groups, and SEBI 50:50 cash-collateral margin rules.
  - `OrderValidationService`: Evaluates all business rules simultaneously without early termination.
  - `OrderExecutionService`: Executes valid trades, debits/credits ledgers, updates holdings, and guarantees account isolation on rejection.
  - `ReportService`: Generates CSV evaluation outputs (`order_decisions.csv`, `contract_notes.csv`, `holdings_report.csv`) and terminal account statements.
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
| **`BR_VAL_005`** | Available Margin / Funds | Available margin must satisfy required margin: 100% of trade value for Delivery Buy, 20% for Intraday. |
| **`BR_VAL_006`** | Free Holdings Check | Delivery Sell requires: $Free Shares = Quantity - Pledged \ge Order Quantity$. |
| **`BR_VAL_007`** | Operating Price Band | Order price must fall strictly within $\pm 10.00\%$ of previous day close ($0.90 \times P_{close} \le P_{order} \le 1.10 \times P_{close}$). |
| **`BR_VAL_008`** | Pledge Collateral Eligibility | Only GROUP_A and GROUP_B scrips are pledgeable. GROUP_C scrips are non-pledgeable (nil value). |
| **`BR_VAL_009`** | Unpledge Quantity Limit | Unpledge quantity cannot exceed currently pledged shares. |

> **Evaluation Requirement Fulfilled:** Validations do not stop after the first failure. All applicable rules are evaluated and reported together as semicolon-delimited rule IDs (e.g., `BR_VAL_007;BR_VAL_006`).

---

## 4. Statutory & Brokerage Charges Engine

All calculations utilize `BigDecimal` with `RoundingMode.HALF_UP` rounded to two decimal places (paisa level) per executed order:

1. **Delivery Brokerage:** ₹0.00 (Zero brokerage on delivery equity).
2. **Intraday Brokerage:** $\min(\max(0.03\% \times \text{Trade Value}, ₹1.00), ₹20.00)$ per executed order.
3. **Securities Transaction Tax (STT):**
   - Delivery: $0.1\%$ on Buy and Sell.
   - Intraday: $0.025\%$ on Sell only ($0\%$ on Buy).
4. **Exchange Transaction Charge:** $0.00345\%$ of Trade Value.
5. **SEBI Turnover Fee:** ₹10 per crore ($0.000001 \times \text{Trade Value}$).
6. **Stamp Duty:**
   - Delivery Buy: $0.015\%$ of Trade Value.
   - Intraday Buy: $0.003\%$ of Trade Value.
   - Sell (both Delivery & Intraday): ₹0.00.
7. **Goods & Services Tax (GST):** $18\%$ on $(\text{Brokerage} + \text{Exchange Charge})$.
8. **Net Settlement Amount:**
   - Buy: $\text{Trade Value} + \text{Total Charges}$ (Cash debited).
   - Sell: $\text{Trade Value} - \text{Total Charges}$ (Cash credited).

#### Benchmark Verification Example (Scenario EC-06):
- **Trade:** Delivery BUY 400 KRISHNAIT @ ₹2,500.00 ($\text{Trade Value} = ₹10,00,000.00$)
- STT ($0.1\%$): ₹1,000.00
- Exchange Charge ($0.00345\%$): ₹34.50
- SEBI Fee (₹10/cr): ₹1.00
- Stamp Duty ($0.015\%$): ₹150.00
- GST ($18\%$ of ₹34.50): ₹6.21
- **Total Charges:** **₹1,191.71**
- **Net Amount Debited:** **₹10,01,191.71** (Verified against engine unit test).

---

## 5. How to Build & Run

### Prerequisites
- Java 17+ LTS (Tested on OpenJDK 21)
- Apache Maven 3.8+
- Git

### Build & Run Unit Tests
```bash
# Run all 34 unit tests
mvn test

# Package JAR file
mvn clean package
```

### Run Week 2 Happy-Path Demonstration
```bash
# Using Java directly
java -jar target/stock-brokerage-engine-1.0.0.jar --demo

# Or using Maven Exec Plugin
mvn exec:java
```

### Run Batch Command File Stream
```bash
java -jar target/stock-brokerage-engine-1.0.0.jar --commands sample-data/T076-PS-009-sample-data/commands.txt
```
This automatically produces the evaluation CSV reports in the `output/` directory:
- `output/order_decisions.csv`
- `output/contract_notes.csv`
- `output/holdings_report.csv`

### Run Interactive Console
```bash
java -jar target/stock-brokerage-engine-1.0.0.jar --interactive
```

---

## 6. Week 2 Happy-Path Demonstration Walkthrough

When running `--demo`, the engine executes and outputs the exact four mandatory steps:

```
==========================================================================================
            CAPSTONE PROJECT: STOCK BROKERAGE MARGIN, SETTLEMENT & PLEDGE ENGINE          
                                  PROBLEM STATEMENT: PS-009                               
                                         TEAM ID: T076                                    
   TEAM MEMBERS: 1. Mohammed Saif (1AT24CG049) | 2. Mohanchandra M (1AT24CG050)           
   BRANCH / SECTION: CSD | DOMAIN: BFSI - Capital Markets | MILESTONE: Week 2 Core Flows  
==========================================================================================

>>> STEP 1: LOADING SAMPLE CLIENTS (CLIENT MANAGEMENT)
------------------------------------------------------------------------------------------
 Successfully loaded 6 registered clients into memory:

 CLIENT ID | CLIENT NAME          | PAN          | MOBILE       |   OPENING CASH | OPENING DATE
 -----------------------------------------------------------------------------------------
 CL-01    | Deepak Khan          | UDHPT9869R   | 9973091141   | Rs        0.00 | 2026-09-30
 CL-02    | Geetha Khan          | XKCPR8447E   | 8055761698   | Rs   180000.00 | 2026-09-30
 CL-03    | Fathima Agarwal      | QQZPA9320W   | 6967273177   | Rs   200000.00 | 2026-09-30
 CL-04    | Varsha Jain          | NGJPI0029O   | 9534778768   | Rs    60000.00 | 2026-09-30
 CL-05    | Keerthi Kamath       | CUIPP5136E   | 9010798193   | Rs  1001190.71 | 2026-09-30
 CL-06    | Arjun Naik           | SYPPW2837S   | 7984162294   | Rs   200000.00 | 2026-09-30
------------------------------------------------------------------------------------------

>>> STEP 2: LOADING STOCK SYMBOLS & PRICES (STOCK PRICE MANAGEMENT)
------------------------------------------------------------------------------------------
 Loaded 15 scrips with pledge groups and price history.
 Displaying active trade master list with base closing prices and +/- 10% operating bands:

 SYMBOL       | COMPANY NAME                   | HAIRCUT GRP | PREV CLOSE |  DAY CLOSE | OPERATING BAND
 -----------------------------------------------------------------------------------------
 SAHYADRI     | Sahyadri Cements Ltd           | GROUP_A    | Rs 1985.00 | Rs 1977.85 | [1786.50 - 2183.50]
 KAVERITEX    | Kaveri Textiles Ltd            | GROUP_A    | Rs 1790.00 | Rs 1794.00 | [1611.00 - 1969.00]
 NANDIPWR     | Nandi Power Ltd                | GROUP_A    | Rs 1004.00 | Rs  999.90 | [903.60 - 1104.40]
 MYSOREBNK    | Mysore Commercial Bank Ltd     | GROUP_A    | Rs 1002.00 | Rs 1000.00 | [901.80 - 1102.20]
 TUNGAFIN     | Tunga Finance Ltd              | GROUP_A    | Rs 1248.00 | Rs 1230.55 | [1123.20 - 1372.80]
 CHAMUNDI     | Chamundi Foods Ltd             | GROUP_A    | Rs  497.00 | Rs  498.00 | [447.30 - 546.70]
 KRISHNAIT    | Krishna Infotech Ltd           | GROUP_A    | Rs 2492.00 | Rs 2465.85 | [2242.80 - 2741.20]
------------------------------------------------------------------------------------------

>>> STEP 3: DISPLAY CLIENT CASH BALANCE & HOLDINGS (BEFORE TRADE) FOR [CL-05]
========================================================================================
   CLIENT ACCOUNT STATEMENT | ID: CL-05    | NAME: Keerthi Kamath
   PAN: CUIPP5136E   | MOBILE: 9010798193   | AS OF: 2026-10-01
========================================================================================
 Ledger Cash Balance     : Rs   1001190.71
 Pledged Collateral Value: Rs         0.00
----------------------------------------------------------------------------------------
 SYMBOL       |    TOTAL |     FREE |  PLEDGED |   AVG COST |        LTP |    MKT VALUE
----------------------------------------------------------------------------------------
 HAMPISTL     |      190 |      190 |        0 |     515.30 |     502.95 |     95560.50
 KAVERITEX    |      260 |      260 |        0 |    1671.80 |    1794.00 |    466440.00
 MYSOREBNK    |       30 |       30 |        0 |     924.25 |    1000.00 |     30000.00
----------------------------------------------------------------------------------------
 Total Demat Portfolio Value: Rs    592000.50
 Total Client Net Worth     : Rs   1593191.21
========================================================================================

>>> STEP 4: PLACING A DELIVERY BUY ORDER
------------------------------------------------------------------------------------------
 Order Request: DEMO-ORD-101 | Client: CL-05 | Action: BUY | Product: DELIVERY
 Stock: KRISHNAIT | Quantity: 400 shares | Order Price: Rs 2500.00

 Margin Calculation (Delivery = 100% of Trade Value):
   Trade Value    = Quantity (400) * Price (2500.00) = Rs 1000000.00
   Delivery Margin = Trade Value * 100%           = Rs 1000000.00

 Validating Order against Business Rules:
   [BR_VAL_001] Client exists: PASS
   [BR_VAL_002] Scrip recognized: PASS
   [BR_VAL_003] Positive quantity (400 > 0): PASS
   [BR_VAL_004] Positive price (Rs 2500.00 > 0): PASS
   [BR_VAL_007] Price band (+/- 10% of prev close): PASS
   [BR_VAL_005] Available funds validation: PASS

 Order Execution Result: ACCEPTED

 Itemized Statutory & Brokerage Charges Breakdown (per executed order):
   -----------------------------------------------------------------------------------
   1. Delivery Brokerage (Rs 0)             : Rs       0.00
   2. Delivery STT (0.1% of trade value)    : Rs    1000.00
   3. Exchange Txn Charge (0.00345%)        : Rs      34.50
   4. SEBI Turnover Fee (Rs 10 per crore)   : Rs       1.00
   5. Stamp Duty (0.015% on Delivery Buy)   : Rs     150.00
   6. GST (18% on Brokerage + Exchange)    : Rs       6.21
   -----------------------------------------------------------------------------------
   TOTAL CHARGES                            : Rs    1191.71
   NET SETTLEMENT AMOUNT (Trade + Charges)  : Rs 1001191.71
   -----------------------------------------------------------------------------------

 Client [CL-05] Account Statement AFTER Executing Delivery Buy:
========================================================================================
   CLIENT ACCOUNT STATEMENT | ID: CL-05    | NAME: Keerthi Kamath
   PAN: CUIPP5136E   | MOBILE: 9010798193   | AS OF: 2026-10-06
========================================================================================
 Ledger Cash Balance     : Rs        -1.00
 Pledged Collateral Value: Rs         0.00
----------------------------------------------------------------------------------------
 SYMBOL       |    TOTAL |     FREE |  PLEDGED |   AVG COST |        LTP |    MKT VALUE
----------------------------------------------------------------------------------------
 HAMPISTL     |      190 |      190 |        0 |     515.30 |     489.10 |     92929.00
 KAVERITEX    |      260 |      260 |        0 |    1671.80 |    1761.20 |    457912.00
 KRISHNAIT    |      400 |      400 |        0 |    2500.00 |    2472.45 |    988980.00
 MYSOREBNK    |       30 |       30 |        0 |     924.25 |    1010.00 |     30300.00
----------------------------------------------------------------------------------------
 Total Demat Portfolio Value: Rs   1570121.00
 Total Client Net Worth     : Rs   1570120.00
========================================================================================
```

---

## 7. Unit Test Suite Summary

Executed via JUnit 5 (`mvn test`):

| Test Class | Test Cases | Status | Key Verifications |
|---|---|---|---|
| `ChargeCalculationServiceTest` | 11 | **PASSED** | Delivery Rs 0 brokerage, Intraday min Rs 1 / cap Rs 20, Delivery STT 0.1%, Intraday STT 0.025% sell only, Exchange 0.00345%, SEBI Rs 10/cr, Stamp Duty, GST 18%, EC-06 benchmark (Rs 1,191.71). |
| `MarginServiceTest` | 4 | **PASSED** | Delivery 100% margin, Intraday 20% margin, Collateral haircuts (Group A 10%, Group B 20%, Group C 100%), SEBI 50:50 cash-collateral rule (EC-02 benchmark). |
| `OrderValidationServiceTest` | 10 | **PASSED** | Client existence (`BR_VAL_001`), Scrip existence (`BR_VAL_002`), Positive Qty (`BR_VAL_003`), Positive Price (`BR_VAL_004`), Price Band $\pm 10\%$ (`BR_VAL_007`), Free Holdings (`BR_VAL_006`), Available Funds (`BR_VAL_005`), Multi-failure simultaneous reporting (`BR_VAL_007;BR_VAL_006`), Group C pledge rejection (`BR_VAL_008`). |
| `OrderExecutionServiceTest` | 5 | **PASSED** | Delivery Buy state update, Delivery Sell ledger credit, State isolation on rejection, Funds-in audit trail, Pledge execution with Rs 20 processing fee. |
| `ClientManagementTest` | 4 | **PASSED** | Unique client registration, Cash ledger credits/debits, Weighted average cost re-calculation across multiple buys, Free vs Pledged shares segregation. |
| **TOTAL** | **34** | **100% PASSED** | Execution time: ~2.9 seconds. |

---

## 8. Git Repository Information

- **Branch:** `main`
- **Commit History:** Structured commits following Conventional Commits (`feat:`, `test:`, `docs:`, `chore:`).
- **Mentor Sharing:** Shareable with college project mentor via GitHub / GitLab / Git bundle.
