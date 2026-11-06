package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.HolidayRepository;
import edu.atria.capstone.repository.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T+1 Delivery Settlement Service Tests")
class SettlementServiceTest {

    private ClientRepository clientRepository;
    private HolidayRepository holidayRepository;
    private LedgerRepository ledgerRepository;
    private SettlementService settlementService;
    private ChargeCalculationService chargeService;

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
        holidayRepository = new HolidayRepository();
        ledgerRepository = new LedgerRepository();
        chargeService = new ChargeCalculationService();
        settlementService = new SettlementService(clientRepository, holidayRepository, ledgerRepository);

        // Holiday: 2026-10-02 (Gandhi Jayanti, Friday)
        holidayRepository.save(new Holiday(LocalDate.of(2026, 10, 2), "Gandhi Jayanti"));

        Client client = new Client("CL-01", "Deepak Khan", "UDHPT9869R", "9973091141",
                new BigDecimal("500000.00"), LocalDate.of(2026, 9, 30));
        client.addOrUpdateHolding("TUNGAFIN", 200, 0, new BigDecimal("1250.00"));
        clientRepository.save(client);
    }

    @Test
    @DisplayName("Settlement date excludes weekends and official holidays")
    void testSettlementDateCalculationExcludesHolidaysAndWeekends() {
        // Trade date: Thursday 2026-10-01. Friday 2026-10-02 is Gandhi Jayanti (holiday).
        // Saturday 10-03 & Sunday 10-04 are weekend -> Settlement date must be Monday 2026-10-05!
        LocalDate tradeDate = LocalDate.of(2026, 10, 1);
        LocalDate settleDate = settlementService.calculateSettlementDate(tradeDate);
        assertEquals(LocalDate.of(2026, 10, 5), settleDate);
    }

    @Test
    @DisplayName("Delivery sell reserves shares immediately on trade date T (Free = Total - Pledged - SoldToday)")
    void testFreeHoldingsReducedImmediatelyOnTradeDate() {
        Client client = clientRepository.findById("CL-01").orElseThrow();
        assertEquals(200, settlementService.getAvailableFreeHoldingQuantity(client, "TUNGAFIN"));

        // Sell 150 shares on trade date T
        Order sellOrder = new Order("ORD-SELL-1", LocalDateTime.now(), "CL-01", "TUNGAFIN",
                TransactionSide.SELL, ProductType.DELIVERY, 150, new BigDecimal("1250.00"));
        sellOrder.setChargesBreakdown(chargeService.calculateAllCharges(ProductType.DELIVERY, TransactionSide.SELL, sellOrder.getTradeValue()));
        settlementService.queueDeliveryOrder(sellOrder, LocalDate.of(2026, 10, 1));

        // Available free shares to sell today should now be 200 - 150 = 50
        assertEquals(50, settlementService.getAvailableFreeHoldingQuantity(client, "TUNGAFIN"));
    }

    @Test
    @DisplayName("Delivery sell grants exactly 80% usable proceeds on trade date T (EC-01)")
    void testEightyPercentImmediateProceedsUsability() {
        // Gross sale: 200 shares @ 1250.00 = Rs 2,50,000
        Order sellOrder = new Order("ORD-SELL-2", LocalDateTime.now(), "CL-01", "TUNGAFIN",
                TransactionSide.SELL, ProductType.DELIVERY, 200, new BigDecimal("1250.00"));
        sellOrder.setChargesBreakdown(chargeService.calculateAllCharges(ProductType.DELIVERY, TransactionSide.SELL, sellOrder.getTradeValue()));
        settlementService.queueDeliveryOrder(sellOrder, LocalDate.of(2026, 10, 9));

        // 80% of Rs 2,50,000 = Rs 2,00,000
        BigDecimal usable80 = settlementService.getAvailableSameDaySellProceeds("CL-01");
        assertEquals(new BigDecimal("200000.00"), usable80);
    }

    @Test
    @DisplayName("T+1 Settlement execution at 08:00 credits cash and debits shares for delivery sell")
    void testSettlementExecutionSettleDate() {
        LocalDate tradeDate = LocalDate.of(2026, 10, 5);
        LocalDate settleDate = LocalDate.of(2026, 10, 6);

        Order sellOrder = new Order("ORD-SELL-3", LocalDateTime.of(tradeDate, java.time.LocalTime.of(10, 0, 0)),
                "CL-01", "TUNGAFIN", TransactionSide.SELL, ProductType.DELIVERY, 100, new BigDecimal("1250.00"));
        ChargesBreakdown charges = chargeService.calculateAllCharges(ProductType.DELIVERY, TransactionSide.SELL, sellOrder.getTradeValue());
        sellOrder.setChargesBreakdown(charges);
        settlementService.queueDeliveryOrder(sellOrder, tradeDate);

        // Before settlement: shares held = 200, cash = 5,00,000
        Client client = clientRepository.findById("CL-01").orElseThrow();
        assertEquals(200, client.getTotalHoldingQuantity("TUNGAFIN"));
        assertEquals(new BigDecimal("500000.00"), client.getCashBalance());

        // Run settlement on 2026-10-06
        List<SettlementObligation> settled = settlementService.executeSettlement(settleDate);
        assertEquals(1, settled.size());

        // After settlement: shares = 100, cash credited by net amount (TradeValue - Charges)
        assertEquals(100, client.getTotalHoldingQuantity("TUNGAFIN"));
        BigDecimal expectedCash = new BigDecimal("500000.00").add(charges.getNetAmount());
        assertEquals(expectedCash, client.getCashBalance());
    }
}
