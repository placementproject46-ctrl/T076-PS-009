package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.LedgerRepository;
import edu.atria.capstone.repository.OrderRepository;
import edu.atria.capstone.repository.StockPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Intraday Management & Real-Time MTM Engine Tests")
class IntradayServiceTest {

    private ClientRepository clientRepository;
    private StockPriceRepository stockPriceRepository;
    private OrderRepository orderRepository;
    private LedgerRepository ledgerRepository;
    private ChargeCalculationService chargeService;
    private MarginService marginService;
    private IntradayService intradayService;

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
        stockPriceRepository = new StockPriceRepository();
        orderRepository = new OrderRepository();
        ledgerRepository = new LedgerRepository();
        chargeService = new ChargeCalculationService();
        marginService = new MarginService(null, stockPriceRepository);
        intradayService = new IntradayService(clientRepository, stockPriceRepository, orderRepository, ledgerRepository, chargeService, marginService);

        Client client = new Client("CL-06", "Arjun Naik", "SYPPW2837S", "7984162294",
                new BigDecimal("200000.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(client);
    }

    @Test
    @DisplayName("Track open intraday position and calculate unrealized MTM profit and loss")
    void testOpenIntradayPositionAndMtmCalculation() {
        // Buy 100 shares @ 1000.00
        Order buyOrder = new Order("ORD-INT-1", LocalDateTime.of(2026, 10, 5, 10, 0, 0),
                "CL-06", "MYSOREBNK", TransactionSide.BUY, ProductType.INTRADAY, 100, new BigDecimal("1000.00"));
        intradayService.recordIntradayOrder(buyOrder);

        assertEquals(1, intradayService.getOpenPositionCount("CL-06"));
        IntradayPosition pos = intradayService.getClientPositions("CL-06").get("MYSOREBNK");
        assertEquals(100, pos.getNetQuantity());
        assertEquals(new BigDecimal("1000.00"), pos.getAvgCost());

        // Price goes up to 1050.00 -> MTM gain = (1050 - 1000) * 100 = Rs +5,000.00
        BigDecimal gain = pos.calculateMtm(new BigDecimal("1050.00"));
        assertEquals(new BigDecimal("5000.00"), gain);

        // Price drops to 950.00 -> MTM loss = (950 - 1000) * 100 = Rs -5,000.00
        BigDecimal loss = pos.calculateMtm(new BigDecimal("950.00"));
        assertEquals(new BigDecimal("-5000.00"), loss);
    }

    @Test
    @DisplayName("Short selling intraday tracks negative netQuantity and profits on price decline")
    void testIntradayShortSelling() {
        // Short sell 50 shares @ 1000.00
        Order shortOrder = new Order("ORD-INT-SHORT", LocalDateTime.of(2026, 10, 5, 10, 15, 0),
                "CL-06", "MYSOREBNK", TransactionSide.SELL, ProductType.INTRADAY, 50, new BigDecimal("1000.00"));
        intradayService.recordIntradayOrder(shortOrder);

        IntradayPosition pos = intradayService.getClientPositions("CL-06").get("MYSOREBNK");
        assertTrue(pos.isShort());
        assertEquals(-50, pos.getNetQuantity());

        // Price falls to 900.00 -> profit = (1000 - 900) * 50 = Rs +5,000.00
        BigDecimal profit = pos.calculateMtm(new BigDecimal("900.00"));
        assertEquals(new BigDecimal("5000.00"), profit);
    }

    @Test
    @DisplayName("15:20 Auto Square-Off automatically closes positions and applies Rs 50 penalty")
    void testAutoSquareOffAt1520WithPenalty() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        stockPriceRepository.save(date, "MYSOREBNK", new BigDecimal("1000.00"));

        Order buyOrder = new Order("ORD-INT-CLOSE", LocalDateTime.of(date, java.time.LocalTime.of(10, 0, 0)),
                "CL-06", "MYSOREBNK", TransactionSide.BUY, ProductType.INTRADAY, 100, new BigDecimal("1000.00"));
        intradayService.recordIntradayOrder(buyOrder);
        assertEquals(1, intradayService.getOpenPositionCount("CL-06"));

        // Execute square-off at 15:20:00
        LocalDateTime ts1520 = LocalDateTime.of(date, java.time.LocalTime.of(15, 20, 0));
        intradayService.executeIntradayAutoSquareOff(ts1520);

        // Position must be closed
        assertEquals(0, intradayService.getOpenPositionCount("CL-06"));

        // Contract note must contain Rs 50 penalty
        ContractNote note = orderRepository.getContractNotes().stream()
                .filter(n -> n.getRefId().startsWith("SQ-CL-06-MYSOREBNK"))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("50.00"), note.getCharges().getPenalty());
    }
}
