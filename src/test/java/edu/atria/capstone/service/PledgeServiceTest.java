package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Collateral Pledge and Unpledge Service Tests")
class PledgeServiceTest {

    private ClientRepository clientRepository;
    private ScripRepository scripRepository;
    private StockPriceRepository stockPriceRepository;
    private HolidayRepository holidayRepository;
    private LedgerRepository ledgerRepository;
    private OrderRepository orderRepository;
    private MarginService marginService;
    private ChargeCalculationService chargeService;
    private OrderValidationService validationService;
    private OrderExecutionService executionService;

    private final LocalDate testDate = LocalDate.of(2026, 10, 19);

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
        scripRepository = new ScripRepository();
        stockPriceRepository = new StockPriceRepository();
        holidayRepository = new HolidayRepository();
        ledgerRepository = new LedgerRepository();
        orderRepository = new OrderRepository();
        chargeService = new ChargeCalculationService();
        marginService = new MarginService(scripRepository, stockPriceRepository);
        validationService = new OrderValidationService(
                clientRepository, scripRepository, stockPriceRepository, holidayRepository, marginService, chargeService, null);
        executionService = new OrderExecutionService(
                clientRepository, orderRepository, ledgerRepository, chargeService, marginService, validationService);

        scripRepository.save(new Scrip("SAHYADRI", "Sahyadri Cements", PledgeGroup.GROUP_A, 1));
        scripRepository.save(new Scrip("HAMPISTL", "Hampi Steel", PledgeGroup.GROUP_B, 1));
        scripRepository.save(new Scrip("BIDARCHEM", "Bidar Chemicals", PledgeGroup.GROUP_C, 1));

        stockPriceRepository.save(testDate, "SAHYADRI", new BigDecimal("2000.00"));
        stockPriceRepository.save(testDate, "HAMPISTL", new BigDecimal("500.00"));

        Client client = new Client("CL-05", "Keerthi Kamath", "CUIPP5136E", "9010798193",
                new BigDecimal("50000.00"), LocalDate.of(2026, 9, 30));
        client.addOrUpdateHolding("HAMPISTL", 100, 0, new BigDecimal("500.00"));
        clientRepository.save(client);
    }

    @Test
    @DisplayName("Pledge moves free shares to pledged and debits Rs 20 processing fee")
    void testSuccessfulPledgeAndFeeDebit() {
        Client client = clientRepository.findById("CL-05").orElseThrow();
        assertEquals(100, client.getFreeHoldingQuantity("HAMPISTL"));
        assertEquals(0, client.getPledgedQuantity("HAMPISTL"));

        boolean success = executionService.executePledge("PLG-101", LocalDateTime.of(testDate, java.time.LocalTime.of(11, 0, 0)),
                "CL-05", "HAMPISTL", 40);

        assertTrue(success);
        assertEquals(60, client.getFreeHoldingQuantity("HAMPISTL"));
        assertEquals(40, client.getPledgedQuantity("HAMPISTL"));

        // Cash debited by Rs 20: 50,000 - 20 = 49,980
        assertEquals(new BigDecimal("49980.00"), client.getCashBalance());
    }

    @Test
    @DisplayName("Pledge fails and does not charge fee when scrip is in GROUP_C (BR_VAL_008)")
    void testPledgeGroupCRejectionNoFee() {
        Client client = clientRepository.findById("CL-05").orElseThrow();
        client.addOrUpdateHolding("BIDARCHEM", 200, 0, new BigDecimal("60.00"));
        BigDecimal cashBefore = client.getCashBalance();

        boolean success = executionService.executePledge("PLG-102", LocalDateTime.of(testDate, java.time.LocalTime.of(11, 0, 0)),
                "CL-05", "BIDARCHEM", 50);

        assertFalse(success);
        // Cash balance unmodified (Rs 20 fee not charged on rejection)
        assertEquals(cashBefore, client.getCashBalance());
        assertEquals(0, client.getPledgedQuantity("BIDARCHEM"));
    }

    @Test
    @DisplayName("Unpledge restores pledged shares back to free holdings")
    void testSuccessfulUnpledge() {
        Client client = clientRepository.findById("CL-05").orElseThrow();
        client.addOrUpdateHolding("SAHYADRI", 100, 100, new BigDecimal("2000.00"));

        boolean success = executionService.executeUnpledge("UNPLG-1", LocalDateTime.of(testDate, java.time.LocalTime.of(12, 0, 0)),
                "CL-05", "SAHYADRI", 50);

        assertTrue(success);
        assertEquals(50, client.getPledgedQuantity("SAHYADRI"));
        assertEquals(50, client.getFreeHoldingQuantity("SAHYADRI"));
    }

    @Test
    @DisplayName("Unpledge rejected when requested quantity exceeds currently pledged quantity (BR_VAL_009)")
    void testUnpledgeExceedingPledgedQuantityRejection() {
        Client client = clientRepository.findById("CL-05").orElseThrow();
        client.addOrUpdateHolding("SAHYADRI", 100, 40, new BigDecimal("2000.00"));

        // Attempt to unpledge 60 when only 40 are pledged
        boolean success = executionService.executeUnpledge("UNPLG-EXCEED", LocalDateTime.of(testDate, java.time.LocalTime.of(12, 0, 0)),
                "CL-05", "SAHYADRI", 60);

        assertFalse(success);
        assertEquals(40, client.getPledgedQuantity("SAHYADRI"));
    }
}
