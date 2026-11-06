# Edge Case Test Report: Stock Brokerage Engine

**Project ID:** T076 | **Problem Statement:** PS-009  
**Domain:** BFSI – Capital Markets | **Checkpoint:** Week 3 (6 November 2026)  
**Team Members:** Mohammed Saif (1AT24CG049), Mohanchandra M (1AT24CG050) | **Section:** CSD  

---

## 1. Overview

To guarantee zero regression and production robustness under adverse market and timing scenarios, 8 high-risk edge cases (`EC-01` to `EC-08`) were designed, implemented, and verified in `src/test/java/edu/atria/capstone/service/EdgeCaseTest.java`.

All 8 tests execute automatically during `mvn test` with 100% pass rates.

---

## 2. Detailed Edge Case Analysis & Results

### EC-01: Friday Sell Proceeds – 80% Usability on Trade Date T
- **Scenario:** A client sells delivery stock on Friday (`2026-10-09`) for a gross trade value of ₹50,000.00. The client has ₹0.00 initial cash.
- **Regulatory Requirement:** In compliance with SEBI settlement regulations, exactly 80% (₹40,000.00) of the net delivery sell proceeds must be unlocked immediately on trade date $T$ for purchasing new positions. The remaining 20% remains locked until $T+1$ settlement on Monday (`2026-10-12`), after skipping the weekend.
- **Verification in Test:**
  - `testEC01_FridaySellMondayBuy_EightyPercentRule()`:
    1. Sell order for 100 shares of `CHAMUNDI` @ ₹500 is accepted on Friday.
    2. Available same-day credit is asserted to be exactly ₹40,000.00 ($50,000 \times 80\%$).
    3. Buy order requiring ₹45,000.00 margin is rejected with `BR_VAL_AVAILABLE_FUNDS`.
    4. Buy order requiring ₹40,000.00 margin is accepted.
    5. On Monday (`2026-10-12`), `settlementService.executeSettlement()` runs, fully crediting net trade proceeds to the cash ledger.
- **Result:** **PASSED**

---

### EC-02: 50:50 Cash-Collateral Rule
- **Scenario:** An intraday order requires a total margin of ₹1,00,000.00 (trade value ₹5,00,000.00). The client possesses ₹40,000.00 in cash and ₹90,000.00 in pledged collateral value.
- **Regulatory Requirement:** Pledged collateral can satisfy at most 50% of the required margin (₹50,000.00). The other 50% must come from cash. Because the client has only ₹40,000.00 cash, available margin is $₹40,000 + \min(₹90,000, ₹50,000) = ₹90,000.00 < ₹1,00,000.00$.
- **Verification in Test:**
  - `testEC02_CollateralCoversFiftyPercentMargin()`:
    1. Order validation evaluates available margin against required margin.
    2. Order is rejected with `BR_VAL_AVAILABLE_FUNDS` (`BR-ORD-005`).
- **Result:** **PASSED**

---

### EC-03: Auto Square-Off at 15:20 with ₹50 Penalty
- **Scenario:** An intraday position is entered at 15:19:59 (1 second before the cutoff). At 15:20:00, the mandatory square-off procedure triggers.
- **Regulatory Requirement:** Brokers must automatically close open intraday positions at 15:20:00. An administrative square-off penalty of ₹50.00 must be charged to the client.
- **Verification in Test:**
  - `testEC03_IntradayPositionAt1519_SquareOffAt1520()`:
    1. Intraday position accepted and recorded at 15:19:59.
    2. `executeIntradayAutoSquareOff()` triggered at 15:20:00.
    3. Open position count drops from 1 to 0.
    4. Contract note `SQ-CL-03-MYSOREBNK` contains penalty charge of ₹50.00.
- **Result:** **PASSED**

---

### EC-04: Collateral Haircut Dynamic Recalculation (Group A to Group C)
- **Scenario:** A client has 100 shares of `CHAMUNDI` pledged. The stock is initially in Group A (10% haircut, factor 0.90) yielding ₹45,000.00 collateral. The scrip is re-tagged to Group C (100% haircut, non-pledgeable).
- **Regulatory Requirement:** Collateral value must immediately reflect the updated risk tag, dropping to ₹0.00 without requiring manual re-pledging.
- **Verification in Test:**
  - `testEC04_RetagGroupAToGroupC_CollateralDropsToNil()`:
    1. Initial collateral asserted at ₹45,000.00.
    2. `scripRepository.retagGroup("CHAMUNDI", PledgeGroup.GROUP_C)` executed.
    3. `marginService.calculateCollateralValue()` re-evaluated.
    4. Collateral value asserted to drop immediately to ₹0.00.
- **Result:** **PASSED**

---

### EC-05: Price Band Boundary Conditions (±10% of Previous Close)
- **Scenario:** Stock `MYSOREBNK` previous close is ₹1,000.00. Lower band is ₹900.00; upper band is ₹1,100.00.
- **Regulatory Requirement:** Orders strictly at the boundary values (₹900.00 and ₹1,100.00) must be accepted. Orders even 0.01% outside (₹899.90 or ₹1,100.10) must be rejected with `BR_VAL_PRICE_BAND` (`BR-ORD-007`).
- **Verification in Test:**
  - `testEC05_PriceBandBoundaryChecks()`:
    1. Order at ₹900.00: Valid.
    2. Order at ₹899.90: Rejected with `BR_VAL_PRICE_BAND`.
    3. Order at ₹1,100.00: Valid.
    4. Order at ₹1,100.10: Rejected with `BR_VAL_PRICE_BAND`.
- **Result:** **PASSED**

---

### EC-06: Negative Ledger Balance – Daily 0.05% Interest
- **Scenario:** An account has a negative ledger balance (-₹10,000.00) at Day 1 EOD (`2026-10-08`).
- **Regulatory Requirement:** Negative balances incur simple interest at 0.05% per day starting from the day after the negative balance occurred.
- **Verification in Test:**
  - `testEC06_NegativeLedgerBalanceOneRupeeInterest()`:
    1. Day 1: negative status noted; no interest charged yet ($₹0.00$).
    2. Day 2: 1 day of interest charged ($10,000 \times 0.05\% = ₹5.00$).
    3. Balance on Day 2 becomes -₹10,005.00.
    4. Also tested with a boundary balance of -₹1.00.
- **Result:** **PASSED**

---

### EC-07: Market Holiday Pledge Rejection
- **Scenario:** A client submits a `PLEDGE` command dated on a market holiday (e.g. Gandhi Jayanti `2026-10-02` or Republic Day `2026-01-26`).
- **Regulatory Requirement:** Operations on market holidays and weekends are rejected before order processing occurs.
- **Verification in Test:**
  - `testEC07_PledgeOnMarketHolidayRejection()`:
    1. `inputValidationService.validateCommandLine()` intercepts the command.
    2. Validation result is asserted to be invalid, returning holiday error indicator `BR_VAL_010`.
- **Result:** **PASSED**

---

### EC-08: MTM Square-Off Priority Tie-Breaking
- **Scenario:** A client holds two open intraday positions (`CHAMUNDI` and `MYSOREBNK`) that have identical current market values (₹40,000.00 each). A severe market move breaches the 80% equity threshold, triggering auto square-off.
- **Regulatory Requirement:** In a tie for largest market value, the position opened earlier (lower `openingOrderId` / earlier timestamp) must be closed first.
- **Verification in Test:**
  - `testEC08_TiedLargestPositionsMtmSquareOffTieBreaker()`:
    1. Position 1 opened with `ORD-010`.
    2. Position 2 opened with `ORD-020`.
    3. Market move causes MTM margin breach with tied market values of ₹40,000.00 each.
    4. `evaluateMtmAndSquareOff()` executes.
    5. Asserted that `ORD-010` (`CHAMUNDI`) is squared off first, generating contract note for `CHAMUNDI`.
- **Result:** **PASSED**

---

## 3. Summary of Edge Case Execution

| Edge Case ID | Title | Test Method | Outcome |
|:---|:---|:---|:---:|
| **EC-01** | Friday Sell Proceeds (80% Usability) | `testEC01_FridaySellMondayBuy_EightyPercentRule` | PASSED |
| **EC-02** | 50:50 Cash-Collateral Margin Rule | `testEC02_CollateralCoversFiftyPercentMargin` | PASSED |
| **EC-03** | 15:20 Auto Square-Off + ₹50 Penalty | `testEC03_IntradayPositionAt1519_SquareOffAt1520` | PASSED |
| **EC-04** | Dynamic Haircut Recalculation (Group A to C) | `testEC04_RetagGroupAToGroupC_CollateralDropsToNil` | PASSED |
| **EC-05** | Price Band ±10% Boundary Checks | `testEC05_PriceBandBoundaryChecks` | PASSED |
| **EC-06** | Negative Ledger Daily 0.05% Interest | `testEC06_NegativeLedgerBalanceOneRupeeInterest` | PASSED |
| **EC-07** | Market Holiday Command Rejection | `testEC07_PledgeOnMarketHolidayRejection` | PASSED |
| **EC-08** | MTM Square-Off Priority Tie-Breaker | `testEC08_TiedLargestPositionsMtmSquareOffTieBreaker` | PASSED |
