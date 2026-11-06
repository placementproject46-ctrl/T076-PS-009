package edu.atria.capstone.service;

import edu.atria.capstone.engine.BrokerageEngine;
import edu.atria.capstone.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Week 3 End-to-End Simulation Tests")
class EndToEndTest {

    private BrokerageEngine engine;

    @BeforeEach
    void setUp() throws IOException {
        engine = new BrokerageEngine();
        Path sampleDataDir = Paths.get("sample-data", "T076-PS-009-sample-data");
        if (Files.exists(sampleDataDir)) {
            engine.getDataLoader().loadAll(sampleDataDir);
        } else {
            var inClients = getClass().getResourceAsStream("/sample-data/clients.csv");
            if (inClients != null) engine.getDataLoader().loadClients(inClients);
            var inScrips = getClass().getResourceAsStream("/sample-data/scrips.csv");
            if (inScrips != null) engine.getDataLoader().loadScrips(inScrips);
            var inHoldings = getClass().getResourceAsStream("/sample-data/holdings_opening.csv");
            if (inHoldings != null) engine.getDataLoader().loadOpeningHoldings(inHoldings);
            var inPrices = getClass().getResourceAsStream("/sample-data/prices.csv");
            if (inPrices != null) engine.getDataLoader().loadPrices(inPrices);
            var inHolidays = getClass().getResourceAsStream("/sample-data/holidays.csv");
            if (inHolidays != null) engine.getDataLoader().loadHolidays(inHolidays);
        }
    }

    @Test
    @DisplayName("End-to-End: Full sample-data load and commands.txt stream execution")
    void testFullSampleDataAndCommandsExecution(@TempDir Path tempDir) throws IOException {
        // Verify initial loaded state
        assertTrue(engine.getClientRepository().findAll().size() >= 5, "Clients should be loaded");
        assertTrue(engine.getScripRepository().findAll().size() >= 5, "Scrips should be loaded");

        Path cmdFile = Paths.get("sample-data", "T076-PS-009-sample-data", "commands.txt");
        assertTrue(Files.exists(cmdFile), "commands.txt must exist in sample-data");

        // Execute commands
        engine.executeCommandFile(cmdFile);

        // Verify order decisions recorded
        List<OrderDecisionRecord> decisions = engine.getOrderRepository().getDecisionRecords();
        assertFalse(decisions.isEmpty(), "Order decisions should not be empty");

        // Verify contract notes generated for accepted orders
        List<ContractNote> contractNotes = engine.getOrderRepository().getContractNotes();
        assertFalse(contractNotes.isEmpty(), "Contract notes should be generated for executed trades");

        // Verify output reports can be written
        Path orderDecisionsCsv = tempDir.resolve("order_decisions.csv");
        Path contractNotesCsv = tempDir.resolve("contract_notes.csv");
        Path eodReportCsv = tempDir.resolve("eod_report.csv");
        Path holdingsReportCsv = tempDir.resolve("holdings_report.csv");

        engine.getReportService().writeOrderDecisionsCsv(orderDecisionsCsv);
        engine.getReportService().writeContractNotesCsv(contractNotesCsv);
        engine.getReportService().writeEodReportCsv(eodReportCsv);
        engine.getReportService().writeHoldingsReportCsv(holdingsReportCsv, engine.getCurrentDate());

        assertTrue(Files.exists(orderDecisionsCsv), "order_decisions.csv should be created");
        assertTrue(Files.exists(contractNotesCsv), "contract_notes.csv should be created");
        assertTrue(Files.exists(eodReportCsv), "eod_report.csv should be created");
        assertTrue(Files.exists(holdingsReportCsv), "holdings_report.csv should be created");

        List<String> orderLines = Files.readAllLines(orderDecisionsCsv);
        assertTrue(orderLines.size() > 1, "order_decisions.csv should contain header and records");

        List<String> noteLines = Files.readAllLines(contractNotesCsv);
        assertTrue(noteLines.size() > 1, "contract_notes.csv should contain header and records");

        List<String> eodLines = Files.readAllLines(eodReportCsv);
        assertTrue(eodLines.size() > 1, "eod_report.csv should contain header and records");

        List<String> holdingsLines = Files.readAllLines(holdingsReportCsv);
        assertTrue(holdingsLines.size() > 1, "holdings_report.csv should contain header and records");
    }

    @Test
    @DisplayName("End-to-End: Happy path trade cycle and client account consistency")
    void testHappyPathTradeCycle() {
        // Register test client
        Client client = new Client("C999", "E2E Test Client", "ABCDE9999Z", "9876599999",
                new BigDecimal("500000.00"), LocalDate.of(2026, 9, 30));
        engine.getClientRepository().save(client);

        // Ensure stock price exists for test date 2026-10-05
        engine.getStockPriceRepository().save(LocalDate.of(2026, 10, 5), "CHAMUNDI", new BigDecimal("500.00"));

        // Execute BUY DELIVERY
        engine.executeCommand("ORDER|ORD-E2E-1|2026-10-05 10:15:00|C999|CHAMUNDI|BUY|DELIVERY|10|500.00");

        OrderDecisionRecord buyDecision = engine.getOrderRepository().getDecisionRecords().stream()
                .filter(o -> "ORD-E2E-1".equals(o.getRefId()))
                .findFirst()
                .orElse(null);

        assertNotNull(buyDecision, "Buy order must be recorded in decisions");
        assertEquals(OrderDecision.ACCEPTED, buyDecision.getDecision(), "Buy order should be accepted");

        // Advance date and settle on 2026-10-06 (T+1)
        engine.executeCommand("SETTLE|2026-10-06");

        // Verify holding delivered on T+1
        Holding holding = client.getHoldings().get("CHAMUNDI");
        assertNotNull(holding, "CHAMUNDI holding should be delivered after settlement");
        assertEquals(10, holding.getQuantity());

        // Now test SELL DELIVERY of the newly acquired stock
        engine.executeCommand("ORDER|ORD-E2E-2|2026-10-06 11:00:00|C999|CHAMUNDI|SELL|DELIVERY|5|500.00");

        OrderDecisionRecord sellDecision = engine.getOrderRepository().getDecisionRecords().stream()
                .filter(o -> "ORD-E2E-2".equals(o.getRefId()))
                .findFirst()
                .orElse(null);

        assertNotNull(sellDecision);
        assertEquals(OrderDecision.ACCEPTED, sellDecision.getDecision(), "Sell order should be accepted");

        // Free quantity should reflect immediate lock of sold shares (10 - 5 = 5)
        int freeQty = engine.getSettlementService().getAvailableFreeHoldingQuantity(client, "CHAMUNDI");
        assertEquals(5, freeQty, "Free quantity should be 10 - 5 = 5");
    }
}
