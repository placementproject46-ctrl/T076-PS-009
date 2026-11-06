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

@DisplayName("Client Management & Holdings Tracking Tests")
class ClientManagementTest {

    private ClientRepository clientRepository;

    @BeforeEach
    void setUp() {
        clientRepository = new ClientRepository();
    }

    @Test
    @DisplayName("Register clients and assign unique client IDs")
    void testClientRegistration() {
        Client c1 = new Client("CL-01", "Mohammed Saif", "ABCDE1234F", "9876543210", new BigDecimal("500000.00"), LocalDate.now());
        Client c2 = new Client("CL-02", "Mohanchandra M", "XYZAB5678C", "9876543211", new BigDecimal("750000.00"), LocalDate.now());

        clientRepository.save(c1);
        clientRepository.save(c2);

        assertEquals(2, clientRepository.count());
        assertTrue(clientRepository.existsById("CL-01"));
        assertTrue(clientRepository.existsById("CL-02"));

        Optional<Client> retrieved = clientRepository.findById("CL-01");
        assertTrue(retrieved.isPresent());
        assertEquals("Mohammed Saif", retrieved.get().getClientName());
        assertEquals(new BigDecimal("500000.00"), retrieved.get().getCashBalance());
    }

    @Test
    @DisplayName("Maintain cash balances with credits and debits")
    void testCashBalanceManagement() {
        Client client = new Client("CL-01", "Test Trader", "PAN1234567", "9000000000", new BigDecimal("10000.00"), LocalDate.now());

        // Credit funds
        client.creditCash(new BigDecimal("5000.00"));
        assertEquals(new BigDecimal("15000.00"), client.getCashBalance());

        // Debit funds
        client.debitCash(new BigDecimal("3000.00"));
        assertEquals(new BigDecimal("12000.00"), client.getCashBalance());
    }

    @Test
    @DisplayName("Maintain stock holdings with weighted average cost calculation")
    void testWeightedAverageCostCalculation() {
        Client client = new Client("CL-01", "Test Trader", "PAN1234567", "9000000000", new BigDecimal("100000.00"), LocalDate.now());

        // Buy 100 shares @ Rs 1000.00 -> total cost = 1,00,000, avg = 1000.00
        client.addShares("RELIANCE", 100, new BigDecimal("1000.00"));
        Holding h1 = client.getHolding("RELIANCE");
        assertNotNull(h1);
        assertEquals(100, h1.getQuantity());
        assertEquals(new BigDecimal("1000.00"), h1.getAvgCost());

        // Buy 100 shares @ Rs 1200.00 -> total cost = 1,00,000 + 1,20,000 = 2,20,000 / 200 = Rs 1100.00
        client.addShares("RELIANCE", 100, new BigDecimal("1200.00"));
        Holding h2 = client.getHolding("RELIANCE");
        assertEquals(200, h2.getQuantity());
        assertEquals(new BigDecimal("1100.00"), h2.getAvgCost());
    }

    @Test
    @DisplayName("Holdings tracking separates total, pledged and free shares correctly")
    void testFreeAndPledgedShares() {
        Client client = new Client("CL-01", "Test Trader", "PAN1234567", "9000000000", new BigDecimal("100000.00"), LocalDate.now());
        client.addOrUpdateHolding("TCS", 300, 100, new BigDecimal("3500.00"));

        assertEquals(300, client.getTotalHoldingQuantity("TCS"));
        assertEquals(100, client.getPledgedQuantity("TCS"));
        assertEquals(200, client.getFreeHoldingQuantity("TCS"));

        // Pledge 50 more shares
        boolean pledged = client.pledgeShares("TCS", 50);
        assertTrue(pledged);
        assertEquals(150, client.getPledgedQuantity("TCS"));
        assertEquals(150, client.getFreeHoldingQuantity("TCS"));

        // Cannot pledge more than free shares (150 free, requesting 200)
        assertFalse(client.pledgeShares("TCS", 200));

        // Unpledge 70 shares
        boolean unpledged = client.unpledgeShares("TCS", 70);
        assertTrue(unpledged);
        assertEquals(80, client.getPledgedQuantity("TCS"));
        assertEquals(220, client.getFreeHoldingQuantity("TCS"));
    }
}
