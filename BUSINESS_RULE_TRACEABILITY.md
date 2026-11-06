# Business Rule Traceability Matrix: Stock Brokerage Engine

**Project ID:** T076 | **Problem Statement:** PS-009  
**Domain:** BFSI – Capital Markets | **Checkpoint:** Week 3 (6 November 2026)  
**Team Members:** Mohammed Saif (1AT24CG049), Mohanchandra M (1AT24CG050) | **Section:** CSD  

---

| Rule ID | Business Rule | Implementation Class/Method | Test Class | Status |
|:---|:---|:---|:---|:---:|
| **BR-REG-001** | Register client with unique ID, name, PAN, mobile, cash balance, and registration date. | `ClientRepository.save(Client)` | `ClientServiceTest`, `ClientManagementTest` | VERIFIED |
| **BR-REG-002** | Prevent registration with duplicate Client ID or duplicate PAN. | `ClientRepository.save(Client)` | `ClientServiceTest` | VERIFIED |
| **BR-REG-003** | Validate PAN format (10-character alphanumeric: `[A-Z]{5}[0-9]{4}[A-Z]{1}`). | `Client.validatePan(String)` | `ClientManagementTest` | VERIFIED |
| **BR-REG-004** | Opening cash balance at client registration must be non-negative. | `Client.constructor` | `ClientServiceTest` | VERIFIED |
| **BR-LED-001** | Ledger entries are strictly immutable once posted; audit trail is sequential. | `LedgerRepository.save(LedgerEntry)` | `ClientServiceTest` | VERIFIED |
| **BR-LED-002** | All cash movements (trades, fees, funds) follow double-entry debit/credit ledger principles. | `OrderExecutionService.executeOrderStateUpdate(...)` | `BrokerageCalculationTest`, `SettlementServiceTest` | VERIFIED |
| **BR-LED-003** | Daily interest of 0.05% applied to negative ledger balance from next business day. | `InterestCalculationService.calculateAndApplyDailyInterest(...)` | `InterestCalculationTest`, `EdgeCaseTest` | VERIFIED |
| **BR-PLG-001** | Only eligible scrips tagged in Pledge Master (Group A or B) can be pledged for collateral. | `OrderValidationService.validatePledge(...)` | `PledgeServiceTest`, `OrderValidationTest` | VERIFIED |
| **BR-PLG-002** | Apply regulatory haircuts: Group A = 10% (factor 0.90), Group B = 25% (factor 0.75), Group C = 100% (factor 0.00). | `PledgeGroup.getCollateralFactor()`, `MarginService.calculateCollateralValue(...)` | `PledgeServiceTest`, `MarginCalculationTest`, `EdgeCaseTest` | VERIFIED |
| **BR-PLG-003** | Dynamic collateral revaluation upon RETAG command or intraday PRICE_UPDATE. | `MarginService.calculateCollateralValue(...)`, `ScripRepository.retagGroup(...)` | `PledgeServiceTest`, `EdgeCaseTest` | VERIFIED |
| **BR-PLG-004** | Pledged quantity cannot exceed available free holdings ($Free = Total - Pledged - SoldToday$). | `OrderValidationService.validatePledge(...)`, `SettlementService.getAvailableFreeHoldingQuantity(...)` | `PledgeServiceTest`, `OrderValidationTest` | VERIFIED |
| **BR-PLG-005** | PLEDGE and UNPLEDGE commands received on market holidays or weekends are rejected. | `InputValidationService.validateCommandLine(...)` | `InputValidationTest`, `EdgeCaseTest` | VERIFIED |
| **BR-ORD-001** | Client existence validation: order rejected if client ID is not registered. | `OrderValidationService.validateOrder(...)` (Rule `BR_VAL_001`) | `OrderValidationTest`, `OrderValidationServiceTest` | VERIFIED |
| **BR-ORD-002** | Scrip existence validation: order rejected if stock symbol is unknown. | `OrderValidationService.validateOrder(...)` (Rule `BR_VAL_002`) | `OrderValidationTest`, `OrderValidationServiceTest` | VERIFIED |
| **BR-ORD-003** | Positive quantity validation: order quantity must be strictly greater than 0. | `OrderValidationService.validateOrder(...)` (Rule `BR_VAL_003`) | `OrderValidationTest`, `OrderValidationServiceTest` | VERIFIED |
| **BR-ORD-004** | Positive price validation: order price must be strictly greater than 0. | `OrderValidationService.validateOrder(...)` (Rule `BR_VAL_004`) | `OrderValidationTest`, `OrderValidationServiceTest` | VERIFIED |
| **BR-ORD-005** | Available funds & margin validation: order rejected if funds < required margin (50:50 rule for Intraday). | `OrderValidationService.validateOrder(...)` (Rule `BR_VAL_005`), `MarginService.calculateAvailableMargin(...)` | `MarginCalculationTest`, `OrderValidationTest`, `EdgeCaseTest` | VERIFIED |
| **BR-ORD-006** | Holdings check for delivery sell: free shares must cover order quantity ($Free = Total - Pledged - SoldToday$). | `OrderValidationService.validateOrder(...)` (Rule `BR_VAL_006`) | `OrderValidationTest`, `SettlementServiceTest` | VERIFIED |
| **BR-ORD-007** | Operating price band check: order price must be within ±10% of previous day close. | `OrderValidationService.validateOrder(...)` (Rule `BR_VAL_007`) | `PriceBandValidationTest`, `EdgeCaseTest` | VERIFIED |
| **BR-ORD-008** | Validation precedence: 1. Holdings check -> 2. Margin check -> 3. Price-band check -> 4. Decision. | `OrderValidationService.validateOrder(...)` | `OrderValidationTest`, `OrderValidationServiceTest` | VERIFIED |
| **BR-ORD-009** | All applicable validation failures collected and reported in order decision; do not stop on first error. | `ValidationResult.addFailure(...)`, `Order.setRuleIds(...)` | `OrderValidationTest`, `OrderValidationServiceTest` | VERIFIED |
| **BR-SET-001** | Delivery trades queued for T+1 settlement; date calculation skips weekends and market holidays. | `SettlementService.calculateSettlementDate(...)` | `SettlementServiceTest`, `EndToEndTest` | VERIFIED |
| **BR-SET-002** | Settlement execution: on T+1, shares debited/credited to demat holdings; full net proceeds credited to cash ledger. | `SettlementService.executeSettlement(...)` | `SettlementServiceTest`, `EndToEndTest` | VERIFIED |
| **BR-SET-003** | Short delivery resolved via auction at closing price + 20% penalty. | `AuctionSettlementService.settleShortDelivery(...)` | `AuctionSettlementTest` | VERIFIED |
| **BR-SET-004** | 80% same-day delivery sale proceeds immediately available for new purchases on trade date T. | `SettlementService.getAvailableSameDaySellProceeds(...)` | `SettlementServiceTest`, `EdgeCaseTest` | VERIFIED |
| **BR-INT-001** | Intraday orders require 20% margin of trade value ($Margin = Quantity \times Price \times 0.20$). | `MarginService.calculateRequiredMargin(...)` | `MarginCalculationTest`, `IntradayServiceTest` | VERIFIED |
| **BR-INT-002** | Intraday short selling permitted without physical holdings; tracked via negative net quantity. | `IntradayService.recordIntradayOrder(...)`, `IntradayPosition.isShort()` | `IntradayServiceTest` | VERIFIED |
| **BR-INT-003** | Real-time MTM evaluation; trigger margin call auto square-off when available equity < 80% of required margin. | `IntradayService.evaluateMtmAndSquareOff(...)` | `IntradayServiceTest`, `EdgeCaseTest` | VERIFIED |
| **BR-INT-004** | MTM square-off tie-breaking: when positions tie for largest value, lower `openingOrderId` closed first. | `IntradayService.selectLargestPositionForSquareOff(...)` | `EdgeCaseTest` | VERIFIED |
| **BR-INT-005** | Mandatory 15:20 auto square-off for all open intraday positions with ₹50 penalty debited. | `IntradayService.executeIntradayAutoSquareOff(...)` | `IntradayServiceTest`, `EdgeCaseTest` | VERIFIED |
| **BR-CHG-001** | Brokerage: Intraday = min(₹20, 0.05% of trade value); Delivery = min(₹20, 0.1% of trade value). | `ChargeCalculationService.calculateBrokerage(...)` | `BrokerageCalculationTest`, `StatutoryChargesTest` | VERIFIED |
| **BR-CHG-002** | STT: Delivery Buy = 0.1%, Delivery Sell = 0.1%, Intraday Sell = 0.025%, Intraday Buy = 0.0%. | `ChargeCalculationService.calculateStt(...)` | `StatutoryChargesTest` | VERIFIED |
| **BR-CHG-003** | Exchange transaction charge: 0.00345% of trade value on all orders. | `ChargeCalculationService.calculateExchangeCharge(...)` | `StatutoryChargesTest` | VERIFIED |
| **BR-CHG-004** | SEBI turnover charge: ₹10 per crore (0.0001% of trade value). | `ChargeCalculationService.calculateSebiFee(...)` | `StatutoryChargesTest` | VERIFIED |
| **BR-CHG-005** | Stamp duty: Delivery Buy = 0.015%, Intraday Buy = 0.003%, Sell = 0.0%. | `ChargeCalculationService.calculateStampDuty(...)` | `StatutoryChargesTest` | VERIFIED |
| **BR-CHG-006** | GST: 18% on (Brokerage + Exchange Transaction Charge). | `ChargeCalculationService.calculateGst(...)` | `StatutoryChargesTest` | VERIFIED |
| **BR-VAL-001** | Centralized validation intercepts and rejects malformed command lines, invalid tokens, and non-trading days. | `InputValidationService.validateCommandLine(...)` | `InputValidationTest`, `EdgeCaseTest` | VERIFIED |
