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

@DisplayName("Margin Calculation Tests")
class MarginCalculationTest {

    private ScripRepository scripRepository;
    private StockPriceRepository stockPriceRepository;
    private MarginService marginService;

    @BeforeEach
    void setUp() {
        scripRepository = new ScripRepository();
        stockPriceRepository = new StockPriceRepository();
        marginService = new MarginService(scripRepository, stockPriceRepository);

        scripRepository.save(new Scrip("SAHYADRI", "Sahyadri Cements", PledgeGroup.GROUP_A, 1));
        scripRepository.save(new Scrip("HAMPISTL", "Hampi Steel", PledgeGroup.GROUP_B, 1));
        scripRepository.save(new Scrip("BIDARCHEM", "Bidar Chemicals", PledgeGroup.GROUP_C, 1));
    }

    @Test
    @DisplayName("Delivery Margin = 100% of Trade Value")
    void testDeliveryMarginRequirement() {
        BigDecimal tradeVal = marginService.calculateTradeValue(400, new BigDecimal("2500.00"));
        assertEquals(new BigDecimal("1000000.00"), tradeVal);

        BigDecimal deliveryMargin = marginService.calculateRequiredMargin(ProductType.DELIVERY, tradeVal);
        assertEquals(new BigDecimal("1000000.00"), deliveryMargin);
    }

    @Test
    @DisplayName("Intraday Margin = 20% of Trade Value")
    void testIntradayMarginRequirement() {
        BigDecimal tradeVal = marginService.calculateTradeValue(1000, new BigDecimal("1800.00"));
        assertEquals(new BigDecimal("1800000.00"), tradeVal);

        BigDecimal intradayMargin = marginService.calculateRequiredMargin(ProductType.INTRADAY, tradeVal);
        assertEquals(new BigDecimal("360000.00"), intradayMargin);
    }

    @Test
    @DisplayName("Collateral valuation applies exact haircuts: GROUP_A 10%, GROUP_B 25%, GROUP_C 100% (nil)")
    void testCollateralValuationWithHaircuts() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        stockPriceRepository.save(date, "SAHYADRI", new BigDecimal("2000.00"));
        stockPriceRepository.save(date, "HAMPISTL", new BigDecimal("500.00"));
        stockPriceRepository.save(date, "BIDARCHEM", new BigDecimal("60.00"));

        Client client = new Client("CL-TEST", "Test Client", "ABCDE1234F", "9876543210", BigDecimal.ZERO, date);

        // 100 SAHYADRI pledged @ 2000 = 2,00,000 * 90% (10% haircut) = 1,80,000
        client.addOrUpdateHolding("SAHYADRI", 100, 100, new BigDecimal("2000.00"));

        // 200 HAMPISTL pledged @ 500 = 1,00,000 * 75% (25% haircut) = 75,000
        client.addOrUpdateHolding("HAMPISTL", 200, 200, new BigDecimal("500.00"));

        // 500 BIDARCHEM pledged @ 60 = 30,000 * 0% (GROUP_C, not pledgeable) = 0
        client.addOrUpdateHolding("BIDARCHEM", 500, 500, new BigDecimal("60.00"));

        BigDecimal totalCollateral = marginService.calculateCollateralValue(client, date);
        // 1,80,000 + 75,000 + 0 = 2,55,000
        assertEquals(new BigDecimal("255000.00"), totalCollateral);
    }

    @Test
    @DisplayName("Collateral can contribute a maximum of 50% of required margin; cash must cover remaining 50%")
    void testCollateralFiftyPercentCapRule() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        stockPriceRepository.save(date, "SAHYADRI", new BigDecimal("2000.00"));

        Client cl02 = new Client("CL-02", "Geetha Khan", "XKCPR8447E", "8055761698", new BigDecimal("180000.00"), date);
        cl02.addOrUpdateHolding("SAHYADRI", 100, 100, new BigDecimal("2000.00"));

        // Collateral value = 1,80,000
        BigDecimal collateral = marginService.calculateCollateralValue(cl02, date);
        assertEquals(new BigDecimal("180000.00"), collateral);

        // Required margin = 3,60,000
        BigDecimal requiredMargin = new BigDecimal("360000.00");
        BigDecimal availableMargin = marginService.calculateAvailableMargin(cl02, date, ProductType.INTRADAY, requiredMargin);

        // Cash 1,80,000 + 50% max collateral 1,80,000 = 3,60,000
        assertEquals(new BigDecimal("360000.00"), availableMargin);
    }
}
