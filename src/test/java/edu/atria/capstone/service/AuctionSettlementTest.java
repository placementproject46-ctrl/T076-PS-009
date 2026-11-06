package edu.atria.capstone.service;

import edu.atria.capstone.model.AuctionRecord;
import edu.atria.capstone.model.Client;
import edu.atria.capstone.model.LedgerEntry;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.LedgerRepository;
import edu.atria.capstone.repository.StockPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Short Delivery Auction Settlement Tests")
class AuctionSettlementTest {

    private ClientRepository clientRepository;
    private StockPriceRepository stockPriceRepository;
    private LedgerRepository ledgerRepository;
    private AuctionSettlementService auctionService;

    private final LocalDate settlementDate = LocalDate.of(2026, 10, 6);

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
        stockPriceRepository = new StockPriceRepository();
        ledgerRepository = new LedgerRepository();
        auctionService = new AuctionSettlementService(clientRepository, stockPriceRepository, ledgerRepository);

        stockPriceRepository.save(settlementDate, "TUNGAFIN", new BigDecimal("1200.00"));

        Client defaultingSeller = new Client("CL-DEF", "Defaulting Trader", "UDHPT9869R", "9973091141",
                new BigDecimal("100000.00"), LocalDate.of(2026, 9, 30));
        clientRepository.save(defaultingSeller);
    }

    @Test
    @DisplayName("Auction applies closing price plus 20% penalty on shortfall quantity value")
    void testShortDeliveryAuctionCalculation() {
        // Shortfall of 50 shares @ close price 1200.00:
        // Base shortfall value = 50 * 1200 = Rs 60,000.00
        // Penalty (20%) = 60,000 * 20% = Rs 12,000.00
        // Total debit = 60,000 + 12,000 = Rs 72,000.00
        AuctionRecord record = auctionService.settleShortDelivery("CL-DEF", "TUNGAFIN", 50, settlementDate);

        assertNotNull(record);
        assertEquals(new BigDecimal("60000.00"), record.getShortfallValue());
        assertEquals(new BigDecimal("12000.00"), record.getPenaltyAmount());
        assertEquals(new BigDecimal("72000.00"), record.getTotalDebit());

        // Client cash balance debited from 1,00,000 - 72,000 = 28,000
        Client client = clientRepository.findById("CL-DEF").orElseThrow();
        assertEquals(new BigDecimal("28000.00"), client.getCashBalance());

        // Verify ledger audit trail
        List<LedgerEntry> entries = ledgerRepository.findByClientId("CL-DEF");
        assertEquals(1, entries.size());
        assertEquals("SHORT_DELIVERY_AUCTION_DEBIT", entries.get(0).getEntryType());
        assertEquals(new BigDecimal("72000.00"), entries.get(0).getDebit());
    }
}
