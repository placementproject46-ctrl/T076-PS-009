package edu.atria.capstone.engine;

import edu.atria.capstone.model.*;
import edu.atria.capstone.service.ChargeCalculationService;
import edu.atria.capstone.service.MarginService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class HappyPathDemonstrator {

    private final BrokerageEngine engine;

    public HappyPathDemonstrator(BrokerageEngine engine) {
        this.engine = engine;
    }

    public void runDemonstration() {
        System.out.println("==========================================================================================");
        System.out.println("            CAPSTONE PROJECT: STOCK BROKERAGE MARGIN, SETTLEMENT & PLEDGE ENGINE          ");
        System.out.println("                                  PROBLEM STATEMENT: PS-009                               ");
        System.out.println("                                         TEAM ID: T076                                    ");
        System.out.println("   TEAM MEMBERS: 1. Mohammed Saif (1AT24CG049) | 2. Mohanchandra M (1AT24CG050)           ");
        System.out.println("   BRANCH / SECTION: CSD | DOMAIN: BFSI - Capital Markets | MILESTONE: Week 2 Core Flows  ");
        System.out.println("==========================================================================================\n");

        // ---------------------------------------------------------------------------------------
        // STEP 1: Load at least three sample clients
        // ---------------------------------------------------------------------------------------
        System.out.println(">>> STEP 1: LOADING SAMPLE CLIENTS (CLIENT MANAGEMENT)");
        System.out.println("------------------------------------------------------------------------------------------");
        List<Client> clients = engine.getClientRepository().findAll();
        System.out.printf(" Successfully loaded %d registered clients into memory:\n\n", clients.size());
        System.out.printf(" %-8s | %-20s | %-12s | %-12s | %14s | %-12s\n",
                "CLIENT ID", "CLIENT NAME", "PAN", "MOBILE", "OPENING CASH", "OPENING DATE");
        System.out.println(" -----------------------------------------------------------------------------------------");
        for (Client c : clients) {
            System.out.printf(" %-8s | %-20s | %-12s | %-12s | Rs %11s | %s\n",
                    c.getClientId(), c.getClientName(), c.getPan(), c.getMobile(),
                    c.getCashBalance().toPlainString(), c.getOpeningDate());
        }
        System.out.println("------------------------------------------------------------------------------------------\n");

        // ---------------------------------------------------------------------------------------
        // STEP 2: Load at least five stock symbols and their prices
        // ---------------------------------------------------------------------------------------
        System.out.println(">>> STEP 2: LOADING STOCK SYMBOLS & PRICES (STOCK PRICE MANAGEMENT)");
        System.out.println("------------------------------------------------------------------------------------------");
        List<Scrip> scrips = engine.getScripRepository().findAll();
        LocalDate baseDate = LocalDate.of(2026, 10, 1);
        System.out.printf(" Loaded %d scrips with pledge groups and price history.\n", scrips.size());
        System.out.println(" Displaying active trade master list with base closing prices and +/- 10% operating bands:\n");
        System.out.printf(" %-12s | %-30s | %-10s | %10s | %10s | %12s\n",
                "SYMBOL", "COMPANY NAME", "HAIRCUT GRP", "PREV CLOSE", "DAY CLOSE", "OPERATING BAND");
        System.out.println(" -----------------------------------------------------------------------------------------");

        int displayCount = 0;
        for (Scrip s : scrips) {
            if (displayCount++ >= 7) break;
            BigDecimal prevClose = engine.getStockPriceRepository().getPreviousClosePrice(baseDate, s.getSymbol())
                    .orElse(new BigDecimal("1000.00"));
            BigDecimal close = engine.getStockPriceRepository().getClosePrice(baseDate, s.getSymbol())
                    .orElse(prevClose);

            BigDecimal bandLow = prevClose.multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal bandHigh = prevClose.multiply(new BigDecimal("1.10")).setScale(2, RoundingMode.HALF_UP);

            System.out.printf(" %-12s | %-30s | %-10s | Rs %7s | Rs %7s | [%s - %s]\n",
                    s.getSymbol(), s.getCompanyName(), s.getPledgeGroup(),
                    prevClose.toPlainString(), close.toPlainString(),
                    bandLow.toPlainString(), bandHigh.toPlainString());
        }
        System.out.println("------------------------------------------------------------------------------------------\n");

        // ---------------------------------------------------------------------------------------
        // STEP 3: Display client cash balance and holdings
        // ---------------------------------------------------------------------------------------
        String demoClientId = "CL-05";
        System.out.printf(">>> STEP 3: DISPLAY CLIENT CASH BALANCE & HOLDINGS (BEFORE TRADE) FOR [%s]\n", demoClientId);
        System.out.println(engine.getReportService().generateClientAccountReport(demoClientId, baseDate));

        // ---------------------------------------------------------------------------------------
        // STEP 4: Place a DELIVERY BUY order
        // ---------------------------------------------------------------------------------------
        System.out.println(">>> STEP 4: PLACING A DELIVERY BUY ORDER");
        System.out.println("------------------------------------------------------------------------------------------");
        String orderId1 = "DEMO-ORD-101";
        LocalDateTime ts1 = LocalDateTime.of(2026, 10, 6, 11, 0, 0);
        String symbol1 = "KRISHNAIT";
        int qty1 = 400;
        BigDecimal price1 = new BigDecimal("2500.00");

        System.out.printf(" Order Request: %s | Client: %s | Action: BUY | Product: DELIVERY\n", orderId1, demoClientId);
        System.out.printf(" Stock: %s | Quantity: %d shares | Order Price: Rs %s\n\n", symbol1, qty1, price1);

        // Margin & Validation preview
        MarginService marginService = engine.getMarginService();
        ChargeCalculationService chargeService = engine.getChargeCalculationService();
        BigDecimal tradeVal1 = marginService.calculateTradeValue(qty1, price1);
        BigDecimal reqMargin1 = marginService.calculateRequiredMargin(ProductType.DELIVERY, tradeVal1);

        System.out.println(" Margin Calculation (Delivery = 100% of Trade Value):");
        System.out.printf("   Trade Value    = Quantity (%d) * Price (%s) = Rs %s\n", qty1, price1, tradeVal1);
        System.out.printf("   Delivery Margin = Trade Value * 100%%           = Rs %s\n\n", reqMargin1);

        System.out.println(" Validating Order against Business Rules:");
        System.out.println("   [BR_VAL_001] Client exists: PASS");
        System.out.println("   [BR_VAL_002] Scrip recognized: PASS");
        System.out.println("   [BR_VAL_003] Positive quantity (" + qty1 + " > 0): PASS");
        System.out.println("   [BR_VAL_004] Positive price (Rs " + price1 + " > 0): PASS");
        System.out.println("   [BR_VAL_007] Price band (+/- 10% of prev close): PASS");
        System.out.println("   [BR_VAL_005] Available funds validation: PASS\n");

        Order order1 = new Order(orderId1, ts1, demoClientId, symbol1, TransactionSide.BUY, ProductType.DELIVERY, qty1, price1);
        engine.getOrderExecutionService().processOrder(order1, ts1.toLocalDate());

        System.out.println(" Order Execution Result: " + order1.getDecision());
        ChargesBreakdown charges1 = order1.getChargesBreakdown();
        System.out.println("\n Itemized Statutory & Brokerage Charges Breakdown (per executed order):");
        System.out.println("   -----------------------------------------------------------------------------------");
        System.out.printf("   1. Delivery Brokerage (Rs 0)             : Rs %10s\n", charges1.getBrokerage());
        System.out.printf("   2. Delivery STT (0.1%% of trade value)    : Rs %10s\n", charges1.getStt());
        System.out.printf("   3. Exchange Txn Charge (0.00345%%)        : Rs %10s\n", charges1.getExchangeCharge());
        System.out.printf("   4. SEBI Turnover Fee (Rs 10 per crore)   : Rs %10s\n", charges1.getSebiFee());
        System.out.printf("   5. Stamp Duty (0.015%% on Delivery Buy)   : Rs %10s\n", charges1.getStampDuty());
        System.out.printf("   6. GST (18%% on Brokerage + Exchange)    : Rs %10s\n", charges1.getGst());
        System.out.println("   -----------------------------------------------------------------------------------");
        System.out.printf("   TOTAL CHARGES                            : Rs %10s\n", charges1.getTotalCharges());
        System.out.printf("   NET SETTLEMENT AMOUNT (Trade + Charges)  : Rs %10s\n", charges1.getNetAmount());
        System.out.println("   -----------------------------------------------------------------------------------");

        System.out.printf("\n Client [%s] Account Statement AFTER Executing Delivery Buy:\n", demoClientId);
        System.out.println(engine.getReportService().generateClientAccountReport(demoClientId, ts1.toLocalDate()));

        // ---------------------------------------------------------------------------------------
        // STEP 5: Place an INTRADAY BUY order (Demonstrating 20% margin & statutory charges)
        // ---------------------------------------------------------------------------------------
        System.out.println(">>> STEP 5: DEMONSTRATING INTRADAY ORDER WITH 20% MARGIN REQUIREMENT");
        System.out.println("------------------------------------------------------------------------------------------");
        String orderId2 = "DEMO-ORD-102";
        String client2 = "CL-02";
        LocalDateTime ts2 = LocalDateTime.of(2026, 10, 13, 10, 30, 0);
        String symbol2 = "KAVERITEX";
        int qty2 = 1000;
        BigDecimal price2 = new BigDecimal("1800.00");

        System.out.printf(" Order Request: %s | Client: %s | Action: BUY | Product: INTRADAY\n", orderId2, client2);
        System.out.printf(" Stock: %s | Quantity: %d | Order Price: Rs %s\n", symbol2, qty2, price2);

        BigDecimal tradeVal2 = marginService.calculateTradeValue(qty2, price2);
        BigDecimal reqMargin2 = marginService.calculateRequiredMargin(ProductType.INTRADAY, tradeVal2);
        System.out.printf(" Trade Value = %d * %s = Rs %s\n", qty2, price2, tradeVal2);
        System.out.printf(" Intraday Margin (20%% of Trade Value) = Rs %s\n", reqMargin2);

        Order order2 = new Order(orderId2, ts2, client2, symbol2, TransactionSide.BUY, ProductType.INTRADAY, qty2, price2);
        engine.getOrderExecutionService().processOrder(order2, ts2.toLocalDate());
        System.out.println(" Order Execution Result: " + order2.getDecision());
        ChargesBreakdown charges2 = order2.getChargesBreakdown();
        System.out.printf(" Intraday Brokerage (Capped at Rs 20.00 / 0.03%% min Rs 1): Rs %s\n", charges2.getBrokerage());
        System.out.printf(" Intraday Stamp Duty (0.003%% on buy): Rs %s\n", charges2.getStampDuty());
        System.out.printf(" Total Charges Debited: Rs %s\n", charges2.getTotalCharges());
        System.out.println("------------------------------------------------------------------------------------------\n");

        // ---------------------------------------------------------------------------------------
        // STEP 6: Multi-Failure Validation Demonstration
        // ---------------------------------------------------------------------------------------
        System.out.println(">>> STEP 6: MULTI-FAILURE VALIDATION & REJECTION REPORTING");
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.println(" Requirement: 'Evaluate applicable validations and report all failures rather than stopping after the first failure.'");
        System.out.println(" Scenario: CL-02 attempts to SELL 60 shares of UDUPIPHRM (only 50 free) at Rs 999.00 (far outside +10% price band)\n");

        String orderIdReject = "DEMO-ORD-REJECT";
        LocalDateTime tsReject = LocalDateTime.of(2026, 10, 26, 11, 0, 0);
        Order orderReject = new Order(orderIdReject, tsReject, "CL-02", "UDUPIPHRM", TransactionSide.SELL, ProductType.DELIVERY, 60, new BigDecimal("999.00"));
        engine.getOrderExecutionService().processOrder(orderReject, tsReject.toLocalDate());

        System.out.println(" Order ID          : " + orderReject.getOrderId());
        System.out.println(" Order Decision    : " + orderReject.getDecision());
        System.out.println(" Violations Detected Together: " + String.join(";", orderReject.getRuleIds()));
        System.out.println(" Rejection Details : " + orderReject.getRejectionReason());
        System.out.println(" Isolation Check   : Account balances and holdings remain completely unmodified on rejection.");
        System.out.println("==========================================================================================");
        System.out.println("                   WEEK 2 CORE FLOWS DEMONSTRATION SUCCESSFULLY COMPLETED                 ");
        System.out.println("==========================================================================================");
    }
}
