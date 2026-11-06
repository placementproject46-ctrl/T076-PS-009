package edu.atria.capstone.service;

import edu.atria.capstone.model.Client;
import edu.atria.capstone.model.PledgeGroup;
import edu.atria.capstone.model.ProductType;
import edu.atria.capstone.model.Scrip;
import edu.atria.capstone.repository.ScripRepository;
import edu.atria.capstone.repository.StockPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Margin Calculation & Collateral Engine Tests")
class MarginServiceTest {

    private ScripRepository scripRepository;
    private StockPriceRepository stockPriceRepository;
    private MarginService marginService;

    @BeforeEach
    void setUp() {
        scripRepository = new ScripRepository();
        stockPriceRepository = new StockPriceRepository();
        marginService = new MarginService(scripRepository, stockPriceRepository);

        // Setup test scrips
        scripRepository.save(new Scrip("SAHYADRI", "Sahyadri Cements", PledgeGroup.GROUP_A, 1));
        scripRepository.save(new Scrip("HAMPISTL", "Hampi Steel", PledgeGroup.GROUP_B, 1));
        scripRepository.save(new Scrip("BIDARCHEM", "Bidar Chemicals", PledgeGroup.GROUP_C, 1));
    }

    @Test
    @DisplayName("Delivery margin must be exactly 100% of trade value")
    void testDeliveryMarginRequirement() {
        BigDecimal price = new BigDecimal("2500.00");
        int qty = 400;
        BigDecimal tradeValue = marginService.calculateTradeValue(qty, price);
        assertEquals(new BigDecimal("1000000.00"), tradeValue);

        BigDecimal deliveryMargin = marginService.calculateRequiredMargin(ProductType.DELIVERY, tradeValue);
        assertEquals(new BigDecimal("1000000.00"), deliveryMargin);
    }

    @Test
    @DisplayName("Intraday margin must be exactly 20% of trade value")
    void testIntradayMarginRequirement() {
        BigDecimal price = new BigDecimal("1800.00");
        int qty = 1000;
        BigDecimal tradeValue = marginService.calculateTradeValue(qty, price);
        assertEquals(new BigDecimal("1800000.00"), tradeValue);

        BigDecimal intradayMargin = marginService.calculateRequiredMargin(ProductType.INTRADAY, tradeValue);
        assertEquals(new BigDecimal("360000.00"), intradayMargin);
    }

    @Test
    @DisplayName("Collateral valuation applies correct haircut rates for GROUP_A (10%), GROUP_B (20%) and GROUP_C (100%)")
    void testCollateralValuationWithHaircuts() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        stockPriceRepository.save(date, "SAHYADRI", new BigDecimal("2000.00"));
        stockPriceRepository.save(date, "HAMPISTL", new BigDecimal("500.00"));
        stockPriceRepository.save(date, "BIDARCHEM", new BigDecimal("60.00"));

        Client client = new Client("CL-TEST", "Test Client", "ABCDE1234F", "9876543210", BigDecimal.ZERO, date);

        // 100 SAHYADRI pledged @ 2000 = 2,00,000 * 90% (GROUP_A) = 1,80,000
        client.addOrUpdateHolding("SAHYADRI", 100, 100, new BigDecimal("2000.00"));

        // 200 HAMPISTL pledged @ 500 = 1,00,000 * 80% (GROUP_B) = 80,000
        client.addOrUpdateHolding("HAMPISTL", 200, 200, new BigDecimal("500.00"));

        // 500 BIDARCHEM pledged @ 60 = 30,000 * 0% (GROUP_C, not pledgeable) = 0
        client.addOrUpdateHolding("BIDARCHEM", 500, 500, new BigDecimal("60.00"));

        BigDecimal totalCollateral = marginService.calculateCollateralValue(client, date);
        // 1,80,000 + 80,000 + 0 = 2,60,000
        assertEquals(new BigDecimal("260000.00"), totalCollateral);
    }

    @Test
    @DisplayName("Verify EC-02 benchmark: Collateral covers exactly 50% and cash covers 50% of intraday margin")
    void testBenchmarkEC02CollateralFiftyPercentRule() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        stockPriceRepository.save(date, "SAHYADRI", new BigDecimal("2000.00"));

        Client cl02 = new Client("CL-02", "Geetha Khan", "XKCPR8447E", "8055761698", new BigDecimal("180000.00"), date);
        cl02.addOrUpdateHolding("SAHYADRI", 100, 100, new BigDecimal("2000.00"));

        // Collateral value = 100 * 2000 * 0.90 = Rs 1,80,000
        BigDecimal collateral = marginService.calculateCollateralValue(cl02, date);
        assertEquals(new BigDecimal("180000.00"), collateral);

        // Required margin = 20% of (1000 * 1800) = Rs 3,60,000
        BigDecimal requiredMargin = new BigDecimal("360000.00");

        BigDecimal availableMargin = marginService.calculateAvailableMargin(cl02, date, ProductType.INTRADAY, requiredMargin);
        // Cash Rs 1,80,000 + 50% max collateral Rs 1,80,000 = Rs 3,60,000
        assertEquals(new BigDecimal("360000.00"), availableMargin);
        assertTrue(availableMargin.compareTo(requiredMargin) >= 0);
    }
}
