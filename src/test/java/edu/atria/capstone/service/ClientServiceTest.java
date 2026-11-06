package edu.atria.capstone.service;

import edu.atria.capstone.model.Client;
import edu.atria.capstone.model.Holding;
import edu.atria.capstone.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Client and Account Management Tests")
class ClientServiceTest {

    private ClientRepository clientRepository;

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
    }

    @Test
    @DisplayName("Register clients and verify unique client IDs")
    void testClientRegistration() {
        Client c1 = new Client("CL-01", "Deepak Khan", "UDHPT9869R", "9973091141", BigDecimal.ZERO, LocalDate.of(2026, 9, 30));
        Client c2 = new Client("CL-02", "Geetha Khan", "XKCPR8447E", "8055761698", new BigDecimal("180000.00"), LocalDate.of(2026, 9, 30));

        clientRepository.save(c1);
        clientRepository.save(c2);

        assertEquals(2, clientRepository.count());
        assertTrue(clientRepository.existsById("CL-01"));
        assertTrue(clientRepository.existsById("CL-02"));

        Optional<Client> opt = clientRepository.findById("CL-02");
        assertTrue(opt.isPresent());
        assertEquals("Geetha Khan", opt.get().getClientName());
        assertEquals(new BigDecimal("180000.00"), opt.get().getCashBalance());
    }

    @Test
    @DisplayName("Maintain cash balances with credits and debits")
    void testCashBalanceManagement() {
        Client client = new Client("CL-03", "Fathima Agarwal", "QQZPA9320W", "6967273177", new BigDecimal("200000.00"), LocalDate.of(2026, 9, 30));

        client.creditCash(new BigDecimal("50000.00"));
        assertEquals(new BigDecimal("250000.00"), client.getCashBalance());

        client.debitCash(new BigDecimal("30000.00"));
        assertEquals(new BigDecimal("220000.00"), client.getCashBalance());
    }

    @Test
    @DisplayName("Maintain settled, pledged, and free stock holdings separately")
    void testPledgedAndFreeHoldingsSeparation() {
        Client client = new Client("CL-02", "Geetha Khan", "XKCPR8447E", "8055761698", new BigDecimal("180000.00"), LocalDate.of(2026, 9, 30));
        // 150 total held, 100 pledged -> 50 free
        client.addOrUpdateHolding("UDUPIPHRM", 150, 100, new BigDecimal("281.45"));

        assertEquals(150, client.getTotalHoldingQuantity("UDUPIPHRM"));
        assertEquals(100, client.getPledgedQuantity("UDUPIPHRM"));
        assertEquals(50, client.getFreeHoldingQuantity("UDUPIPHRM"));
    }

    @Test
    @DisplayName("Weighted average cost recalculated accurately upon subsequent buy executions")
    void testWeightedAverageCostRecalculation() {
        Client client = new Client("CL-01", "Deepak Khan", "UDHPT9869R", "9973091141", BigDecimal.ZERO, LocalDate.of(2026, 9, 30));

        // 100 shares @ 1000.00 -> total cost 1,00,000, avg 1000.00
        client.addShares("TUNGAFIN", 100, new BigDecimal("1000.00"));
        Holding h1 = client.getHolding("TUNGAFIN");
        assertEquals(100, h1.getQuantity());
        assertEquals(new BigDecimal("1000.00"), h1.getAvgCost());

        // 100 shares @ 1200.00 -> total cost 2,20,000 / 200 = 1100.00
        client.addShares("TUNGAFIN", 100, new BigDecimal("1200.00"));
        Holding h2 = client.getHolding("TUNGAFIN");
        assertEquals(200, h2.getQuantity());
        assertEquals(new BigDecimal("1100.00"), h2.getAvgCost());
    }
}
