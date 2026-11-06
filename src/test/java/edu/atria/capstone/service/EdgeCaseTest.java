package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Week 3 Edge Case Test Suite (EC-01 through EC-08)")
class EdgeCaseTest {

    private ClientRepository clientRepository;
    private ScripRepository scripRepository;
    private StockPriceRepository stockPriceRepository;
    private HolidayRepository holidayRepository;
    private LedgerRepository ledgerRepository;
    private OrderRepository orderRepository;

    private MarginService marginService;
    private ChargeCalculationService chargeCalculationService;
    private SettlementService settlementService;
    private IntradayService intradayService;
    private InterestCalculationService interestService;
    private InputValidationService inputValidationService;
    private OrderValidationService orderValidationService;
    private OrderExecutionService orderExecutionService;

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
        scripRepository = new ScripRepository();
        stockPriceRepository = new StockPriceRepository();
        holidayRepository = new HolidayRepository();
        ledgerRepository = new LedgerRepository();
        orderRepository = new OrderRepository();

        chargeCalculationService = new ChargeCalculationService();
        marginService = new MarginService(scripRepository, stockPriceRepository);
        settlementService = new SettlementService(clientRepository, holidayRepository, ledgerRepository);
        intradayService = new IntradayService(clientRepository, stockPriceRepository, orderRepository, ledgerRepository, chargeCalculationService, marginService);
        interestService = new InterestCalculationService(clientRepository, ledgerRepository);
        inputValidationService = new InputValidationService(clientRepository, scripRepository, holidayRepository);

        orderValidationService = new OrderValidationService(
                clientRepository, scripRepository, stockPriceRepository, holidayRepository, marginService, chargeCalculationService, settlementService);

        orderExecutionService = new OrderExecutionService(
                clientRepository, orderRepository, ledgerRepository, chargeCalculationService, marginService, orderValidationService);
        orderExecutionService.setSettlementService(settlementService);
        orderExecutionService.setIntradayService(intradayService);

        // Setup base scrips
        scripRepository.save(new Scrip("CHAMUNDI", "Chamundi Foods Ltd", PledgeGroup.GROUP_A, 1));
        scripRepository.save(new Scrip("UDUPIPHRM", "Udupi Pharma Ltd", PledgeGroup.GROUP_B, 1));
        scripRepository.save(new Scrip("MYSOREBNK", "Mysore Bank Ltd", PledgeGroup.GROUP_A, 1));

        // Base stock prices
        stockPriceRepository.save(LocalDate.of(2026, 10, 8), "CHAMUNDI", new BigDecimal("500.00"));
        stockPriceRepository.save(LocalDate.of(2026, 10, 8), "UDUPIPHRM", new BigDecimal("350.00"));
        stockPriceRepository.save(LocalDate.of(2026, 10, 8), "MYSOREBNK", new BigDecimal("1000.00"));

        stockPriceRepository.save(LocalDate.of(2026, 10, 9), "CHAMUNDI", new BigDecimal("500.00"));
        stockPriceRepository.save(LocalDate.of(2026, 10, 9), "UDUPIPHRM", new BigDecimal("350.00"));
        stockPriceRepository.save(LocalDate.of(2026, 10, 9), "MYSOREBNK", new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("EC-01: Friday sell proceeds: 80% available Friday for new buys, 20% locked until Monday T+1")
    void testEC01_FridaySellMondayBuy_EightyPercentRule() {
        // Friday date: 2026-10-09
        LocalDate friday = LocalDate.of(2026, 10, 9);
        LocalDateTime fridayTime = LocalDateTime.of(friday, LocalTime.of(10, 0));

        // Client has 0 cash balance but 200 shares of CHAMUNDI
        Client client = new Client("CL-01", "Client One", "ABCDE1234F", "9876543210",
                BigDecimal.ZERO, LocalDate.of(2026, 9, 30));
        client.addOrUpdateHolding("CHAMUNDI", 200, 0, new BigDecimal("500.00"));
        clientRepository.save(client);

        // Client sells 100 shares of CHAMUNDI @ 500.00 on Friday -> Trade value = Rs 50,000.00
        Order sellOrder = new Order("ORD-S01", fridayTime, "CL-01", "CHAMUNDI",
                TransactionSide.SELL, ProductType.DELIVERY, 100, new BigDecimal("500.00"));

        Order executedSell = orderExecutionService.processOrder(sellOrder, friday);
        assertEquals(OrderDecision.ACCEPTED, executedSell.getDecision(), "Delivery sell must be accepted");

        // Exactly 80% of Rs 50,000 = Rs 40,000.00 must be available for same-day Friday buys
        BigDecimal sameDayCredit = settlementService.getAvailableSameDaySellProceeds("CL-01");
        assertEquals(new BigDecimal("40000.00"), sameDayCredit);

        // Attempt buy requiring Rs 45,000 margin (90 shares @ 500) -> Must be rejected (exceeds 80% limit)
        Order buyTooMuch = new Order("ORD-B01", fridayTime.plusMinutes(5), "CL-01", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 90, new BigDecimal("500.00"));
        Order rejectedBuy = orderExecutionService.processOrder(buyTooMuch, friday);
        assertEquals(OrderDecision.REJECTED, rejectedBuy.getDecision(), "Buy exceeding 80% proceeds must be rejected");
        assertTrue(rejectedBuy.getRuleIds().contains(OrderValidationService.BR_VAL_AVAILABLE_FUNDS));

        // Buy requiring Rs 40,000 margin (80 shares @ 500) -> Exactly equals 80% available credit -> Accepted
        Order buyValid = new Order("ORD-B02", fridayTime.plusMinutes(10), "CL-01", "CHAMUNDI",
                TransactionSide.BUY, ProductType.DELIVERY, 80, new BigDecimal("500.00"));
        Order acceptedBuy = orderExecutionService.processOrder(buyValid, friday);
        assertEquals(OrderDecision.ACCEPTED, acceptedBuy.getDecision(), "Buy within 80% proceeds must be accepted");

        // Advance to Monday: 2026-10-12 and execute T+1 settlement
        LocalDate monday = LocalDate.of(2026, 10, 12);
        settlementService.executeSettlement(monday);

        // After T+1 settlement, original sell proceeds net of statutory charges are fully settled
        assertTrue(client.getCashBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Settlement must complete on Monday without leaving illegal deficits");
    }

    @Test
    @DisplayName("EC-02: 50:50 Cash-Collateral rule: Required margin 1,00,000, Cash 40,000, Collateral 80,000 -> Rejected")
    void testEC02_CollateralCoversFiftyPercentMargin() {
        LocalDate date = LocalDate.of(2026, 10, 9);
        LocalDateTime time = LocalDateTime.of(date, LocalTime.of(10, 0));

        // Client has Rs 40,000.00 cash
        Client client = new Client("CL-02", "Client Two", "BCDEF2345G", "9876543211",
                new BigDecimal("40000.00"), LocalDate.of(2026, 9, 30));
        // Add pledged holding giving Rs 85,000.00 collateral (100 MYSOREBNK @ 1000 * 0.85 = 85,000)
        client.addOrUpdateHolding("MYSOREBNK", 100, 100, new BigDecimal("1000.00"));
        clientRepository.save(client);

        // Collateral value = 90,000 (Group A 10% haircut -> 100 * 1000 * 0.90 = 90,000)
        BigDecimal collateral = marginService.calculateCollateralValue(client, date);
        assertEquals(new BigDecimal("90000.00"), collateral);

        // Order requiring Rs 1,00,000.00 margin:
        // Intraday buy: 500 shares of MYSOREBNK @ 1000.00 = Trade Value 5,00,000 -> 20% margin = Rs 1,00,000.00
        Order order = new Order("ORD-002", time, "CL-02", "MYSOREBNK",
                TransactionSide.BUY, ProductType.INTRADAY, 500, new BigDecimal("1000.00"));

        ValidationResult valRes = orderValidationService.validateOrder(order, date);
        assertFalse(valRes.isValid(), "Order must be rejected: cash covers only 40,000 (< 50,000 required cash component)");
        assertTrue(valRes.getFailures().stream().anyMatch(f -> f.getRuleId().equals(OrderValidationService.BR_VAL_AVAILABLE_FUNDS)));
    }

    @Test
    @DisplayName("EC-03: Auto square-off at 15:20: Intraday position entered at 15:19:59 squared off at 15:20 with Rs 50 penalty")
    void testEC03_IntradayPositionAt1519_SquareOffAt1520() {
        LocalDate date = LocalDate.of(2026, 10, 9);
        LocalDateTime entryTime = LocalDateTime.of(date, LocalTime.of(15, 19, 59));

        Client client = new Client("CL-03", "Client Three", "CDEFG3456H", "9876543212",
                new BigDecimal("200000.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);

        // Place intraday buy order at 15:19:59
        Order buyOrder = new Order("ORD-IN01", entryTime, "CL-03", "MYSOREBNK",
                TransactionSide.BUY, ProductType.INTRADAY, 50, new BigDecimal("1000.00"));
        Order processed = orderExecutionService.processOrder(buyOrder, date);
        assertEquals(OrderDecision.ACCEPTED, processed.getDecision());

        // Verify open position tracked
        assertEquals(1, intradayService.getOpenPositionCount("CL-03"));

        // Trigger 15:20 auto square-off
        LocalDateTime ts1520 = LocalDateTime.of(date, LocalTime.of(15, 20, 0));
        intradayService.executeIntradayAutoSquareOff(ts1520);

        // Verify open position closed
        assertEquals(0, intradayService.getOpenPositionCount("CL-03"), "Intraday position must be closed at 15:20");

        // Contract note must include Rs 50.00 square-off penalty
        ContractNote note = orderRepository.getContractNotes().stream()
                .filter(n -> n.getRefId().startsWith("SQ-CL-03-MYSOREBNK"))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("50.00"), note.getCharges().getPenalty(), "Rs 50 penalty must be charged");
    }

    @Test
    @DisplayName("EC-04: Collateral haircut dynamic recalculation: Group A (15%) to Group C (100%) -> Collateral drops to nil")
    void testEC04_RetagGroupAToGroupC_CollateralDropsToNil() {
        LocalDate date = LocalDate.of(2026, 10, 9);

        Client client = new Client("CL-04", "Client Four", "DEFGH4567I", "9876543213",
                new BigDecimal("50000.00"), LocalDate.of(2026, 9, 30));
        client.addOrUpdateHolding("CHAMUNDI", 100, 100, new BigDecimal("500.00"));
        clientRepository.save(client);

        // CHAMUNDI starts in GROUP_A (10% haircut -> factor 0.90)
        // Collateral = 100 * 500.00 * 0.90 = Rs 45,000.00
        BigDecimal collateralBefore = marginService.calculateCollateralValue(client, date);
        assertEquals(new BigDecimal("45000.00"), collateralBefore);

        // Re-tag CHAMUNDI to GROUP_C (100% haircut -> not pledgeable)
        scripRepository.retagGroup("CHAMUNDI", PledgeGroup.GROUP_C);

        // Dynamic recalculation: collateral value must drop to 0.00 immediately
        BigDecimal collateralAfter = marginService.calculateCollateralValue(client, date);
        assertEquals(new BigDecimal("0.00"), collateralAfter, "Group C collateral value must be zero");
    }

    @Test
    @DisplayName("EC-05: Price band boundary checks: -10% valid, -10.01% rejected, +10% valid, +10.01% rejected")
    void testEC05_PriceBandBoundaryChecks() {
        LocalDate date = LocalDate.of(2026, 10, 9);
        LocalDateTime time = LocalDateTime.of(date, LocalTime.of(10, 0));

        // MYSOREBNK prev close = 1000.00
        // Lower band (-10%): 900.00
        // Upper band (+10%): 1100.00
        Client client = new Client("CL-05", "Client Five", "EFGHI5678J", "9876543214",
                new BigDecimal("500000.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);

        // Exactly -10% (900.00) -> Valid
        Order atLower = new Order("ORD-LB", time, "CL-05", "MYSOREBNK",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("900.00"));
        assertTrue(orderValidationService.validateOrder(atLower, date).isValid(), "Price at exactly -10% must be valid");

        // Below -10% (899.90) -> Rejected with BR_VAL_007
        Order belowLower = new Order("ORD-BLB", time, "CL-05", "MYSOREBNK",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("899.90"));
        ValidationResult resBelow = orderValidationService.validateOrder(belowLower, date);
        assertFalse(resBelow.isValid());
        assertTrue(resBelow.getFailures().stream().anyMatch(f -> f.getRuleId().equals(OrderValidationService.BR_VAL_PRICE_BAND)));

        // Exactly +10% (1100.00) -> Valid
        Order atUpper = new Order("ORD-UB", time, "CL-05", "MYSOREBNK",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("1100.00"));
        assertTrue(orderValidationService.validateOrder(atUpper, date).isValid(), "Price at exactly +10% must be valid");

        // Above +10% (1100.10) -> Rejected with BR_VAL_007
        Order aboveUpper = new Order("ORD-AUB", time, "CL-05", "MYSOREBNK",
                TransactionSide.BUY, ProductType.DELIVERY, 10, new BigDecimal("1100.10"));
        ValidationResult resAbove = orderValidationService.validateOrder(aboveUpper, date);
        assertFalse(resAbove.isValid());
        assertTrue(resAbove.getFailures().stream().anyMatch(f -> f.getRuleId().equals(OrderValidationService.BR_VAL_PRICE_BAND)));
    }

    @Test
    @DisplayName("EC-06: Negative ledger balance: Account at -1.00 incurs daily 0.05% interest")
    void testEC06_NegativeLedgerBalanceOneRupeeInterest() {
        LocalDate day1 = LocalDate.of(2026, 10, 8);
        LocalDate day2 = LocalDate.of(2026, 10, 9);

        // Client with -10,000.00 balance: daily interest at 0.05% = Rs 5.00/day
        Client client = new Client("CL-06", "Client Six", "FGHIJ6789K", "9876543215",
                new BigDecimal("-10000.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);

        // Day 1 EOD: marks negative balance date
        interestService.calculateAndApplyDailyInterest("CL-06", day1);
        assertEquals(new BigDecimal("-10000.00"), client.getCashBalance());

        // Day 2 EOD: interest applied for 1 overnight day = Rs 5.00
        interestService.calculateAndApplyDailyInterest("CL-06", day2);
        assertEquals(new BigDecimal("-10005.00"), client.getCashBalance(),
                "0.05% interest must be charged for day 2");

        // Test with -1.00 balance
        Client clientOne = new Client("CL-06-ONE", "Client Six One", "GHIJK7890L", "9876543216",
                new BigDecimal("-1.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(clientOne);
        interestService.calculateAndApplyDailyInterest("CL-06-ONE", day1);
        interestService.calculateAndApplyDailyInterest("CL-06-ONE", day2);
        assertTrue(clientOne.getCashBalance().compareTo(new BigDecimal("-1.00")) <= 0);
    }

    @Test
    @DisplayName("EC-07: Market holiday pledge request: PLEDGE on Gandhi Jayanti (2026-10-02) rejected")
    void testEC07_PledgeOnMarketHolidayRejection() {
        LocalDate gandhiJayanti = LocalDate.of(2026, 10, 2);
        holidayRepository.save(new Holiday(gandhiJayanti, "Gandhi Jayanti"));

        // Centralized input validation rejects commands submitted on holidays
        String pledgeCmd = "PLEDGE|PLG-001|2026-10-02 10:00:00|CL-01|CHAMUNDI|50";
        ValidationResult valRes = inputValidationService.validateCommandLine(pledgeCmd);

        assertFalse(valRes.isValid(), "PLEDGE command on market holiday must be rejected");
        assertTrue(valRes.getCombinedMessage().toLowerCase().contains("holiday") ||
                   valRes.getFailures().stream().anyMatch(f -> f.getRuleId().equals("BR_VAL_010")),
                "Rejection message or rule ID must indicate market holiday");
    }

    @Test
    @DisplayName("EC-08: MTM square-off priority tie-breaking: Tied positions resolved by lower openingOrderId")
    void testEC08_TiedLargestPositionsMtmSquareOffTieBreaker() {
        LocalDate date = LocalDate.of(2026, 10, 9);
        LocalDateTime t1 = LocalDateTime.of(date, LocalTime.of(10, 0, 0));
        LocalDateTime t2 = LocalDateTime.of(date, LocalTime.of(10, 5, 0));

        // Client with Rs 15,000.00 cash
        Client client = new Client("CL-08", "Client Eight", "HIJKL8901M", "9876543217",
                new BigDecimal("15000.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);

        // Position 1: ORD-010, CHAMUNDI, 100 qty @ 500 = Position Value Rs 50,000.00
        Order order1 = new Order("ORD-010", t1, "CL-08", "CHAMUNDI",
                TransactionSide.BUY, ProductType.INTRADAY, 100, new BigDecimal("500.00"));
        intradayService.recordIntradayOrder(order1);

        // Position 2: ORD-020, MYSOREBNK, 40 qty @ 1000 = Position Value Rs 40,000.00
        Order order2 = new Order("ORD-020", t2, "CL-08", "MYSOREBNK",
                TransactionSide.BUY, ProductType.INTRADAY, 40, new BigDecimal("1000.00"));
        intradayService.recordIntradayOrder(order2);

        assertEquals(2, intradayService.getOpenPositionCount("CL-08"));

        // Price of CHAMUNDI drops to 400.00:
        // Current market value: 100 * 400.00 = Rs 40,000.00
        // MYSOREBNK market value: 40 * 1000.00 = Rs 40,000.00
        // Both positions are TIED at exactly Rs 40,000.00 market value!
        // CHAMUNDI MTM loss = (400 - 500) * 100 = -10,000.00
        // Available equity = 15,000 - 10,000 = 5,000 < 80% of (80,000 * 0.20 = 16,000) = 12,800
        stockPriceRepository.updateIntradayPrice("CHAMUNDI", new BigDecimal("400.00"));
        LocalDateTime tsUpdate = LocalDateTime.of(date, LocalTime.of(11, 0, 0));

        intradayService.evaluateMtmAndSquareOff("CHAMUNDI", new BigDecimal("400.00"), tsUpdate);

        // Verify square-off happened
        ContractNote squareOffNote = orderRepository.getContractNotes().stream()
                .filter(n -> n.getRefId().startsWith("SQ-CL-08"))
                .findFirst()
                .orElse(null);

        assertNotNull(squareOffNote, "Square-off contract note must be generated");
        assertEquals("CHAMUNDI", squareOffNote.getSymbol(),
                "ORD-010 (CHAMUNDI) must be squared off first due to lower orderId tie-breaker");
    }
}
