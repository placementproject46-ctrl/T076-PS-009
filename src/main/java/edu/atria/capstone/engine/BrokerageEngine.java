package edu.atria.capstone.engine;

import edu.atria.capstone.loader.CsvDataLoader;
import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.*;
import edu.atria.capstone.service.*;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BrokerageEngine {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ClientRepository clientRepository = new ClientRepository();
    private final ScripRepository scripRepository = new ScripRepository();
    private final StockPriceRepository stockPriceRepository = new StockPriceRepository();
    private final HolidayRepository holidayRepository = new HolidayRepository();
    private final OrderRepository orderRepository = new OrderRepository();
    private final LedgerRepository ledgerRepository = new LedgerRepository();

    private final ChargeCalculationService chargeCalculationService = new ChargeCalculationService();
    private final MarginService marginService;
    private final OrderValidationService orderValidationService;
    private final OrderExecutionService orderExecutionService;
    private final ReportService reportService;
    private final CsvDataLoader dataLoader;

    private LocalDate currentDate = LocalDate.of(2026, 10, 1);

    public BrokerageEngine() {
        this.marginService = new MarginService(scripRepository, stockPriceRepository);
        this.orderValidationService = new OrderValidationService(
                clientRepository, scripRepository, stockPriceRepository, marginService, chargeCalculationService);
        this.orderExecutionService = new OrderExecutionService(
                clientRepository, orderRepository, ledgerRepository, chargeCalculationService, marginService, orderValidationService);
        this.reportService = new ReportService(
                clientRepository, orderRepository, ledgerRepository, stockPriceRepository, marginService);
        this.dataLoader = new CsvDataLoader(
                clientRepository, scripRepository, stockPriceRepository, holidayRepository);
    }

    public ClientRepository getClientRepository() {
        return clientRepository;
    }

    public ScripRepository getScripRepository() {
        return scripRepository;
    }

    public StockPriceRepository getStockPriceRepository() {
        return stockPriceRepository;
    }

    public HolidayRepository getHolidayRepository() {
        return holidayRepository;
    }

    public OrderRepository getOrderRepository() {
        return orderRepository;
    }

    public LedgerRepository getLedgerRepository() {
        return ledgerRepository;
    }

    public ChargeCalculationService getChargeCalculationService() {
        return chargeCalculationService;
    }

    public MarginService getMarginService() {
        return marginService;
    }

    public OrderValidationService getOrderValidationService() {
        return orderValidationService;
    }

    public OrderExecutionService getOrderExecutionService() {
        return orderExecutionService;
    }

    public ReportService getReportService() {
        return reportService;
    }

    public CsvDataLoader getDataLoader() {
        return dataLoader;
    }

    public LocalDate getCurrentDate() {
        return currentDate;
    }

    public void setCurrentDate(LocalDate currentDate) {
        this.currentDate = currentDate;
    }

    /**
     * Executes a command string.
     */
    public void executeCommand(String commandLine) {
        if (commandLine == null) return;
        String line = commandLine.trim();
        if (line.isEmpty() || line.startsWith("#")) return;

        String[] tokens = line.split("\\|", -1);
        String cmd = tokens[0].trim().toUpperCase();

        switch (cmd) {
            case "ORDER" -> handleOrderCommand(tokens);
            case "PLEDGE" -> handlePledgeCommand(tokens);
            case "UNPLEDGE" -> handleUnpledgeCommand(tokens);
            case "FUNDS_IN" -> handleFundsInCommand(tokens);
            case "RETAG" -> handleRetagCommand(tokens);
            case "PRICE_UPDATE" -> handlePriceUpdateCommand(tokens);
            case "SETTLE" -> handleSettleCommand(tokens);
            case "INTRADAY_SQUARE_OFF" -> handleSquareOffCommand(tokens);
            case "EOD" -> handleEodCommand(tokens);
            case "PRINT_REPORT" -> handlePrintReportCommand(tokens);
            default -> System.err.println("Unknown command: " + cmd);
        }
    }

    private void handleOrderCommand(String[] tokens) {
        // ORDER|order_id|timestamp|client_id|symbol|side|product|quantity|price
        if (tokens.length < 9) return;
        String orderId = tokens[1].trim();
        LocalDateTime ts = LocalDateTime.parse(tokens[2].trim(), TIMESTAMP_FORMATTER);
        String clientId = tokens[3].trim();
        String symbol = tokens[4].trim().toUpperCase();
        TransactionSide side = TransactionSide.fromString(tokens[5].trim());
        ProductType product = ProductType.fromString(tokens[6].trim());
        int qty = Integer.parseInt(tokens[7].trim());
        BigDecimal price = new BigDecimal(tokens[8].trim()).setScale(2, RoundingMode.HALF_UP);

        this.currentDate = ts.toLocalDate();

        Order order = new Order(orderId, ts, clientId, symbol, side, product, qty, price);
        orderExecutionService.processOrder(order, currentDate);
    }

    private void handlePledgeCommand(String[] tokens) {
        // PLEDGE|request_id|timestamp|client_id|symbol|quantity
        if (tokens.length < 6) return;
        String reqId = tokens[1].trim();
        LocalDateTime ts = LocalDateTime.parse(tokens[2].trim(), TIMESTAMP_FORMATTER);
        String clientId = tokens[3].trim();
        String symbol = tokens[4].trim().toUpperCase();
        int qty = Integer.parseInt(tokens[5].trim());

        this.currentDate = ts.toLocalDate();
        orderExecutionService.executePledge(reqId, ts, clientId, symbol, qty);
    }

    private void handleUnpledgeCommand(String[] tokens) {
        // UNPLEDGE|request_id|timestamp|client_id|symbol|quantity
        if (tokens.length < 6) return;
        String reqId = tokens[1].trim();
        LocalDateTime ts = LocalDateTime.parse(tokens[2].trim(), TIMESTAMP_FORMATTER);
        String clientId = tokens[3].trim();
        String symbol = tokens[4].trim().toUpperCase();
        int qty = Integer.parseInt(tokens[5].trim());

        this.currentDate = ts.toLocalDate();
        orderExecutionService.executeUnpledge(reqId, ts, clientId, symbol, qty);
    }

    private void handleFundsInCommand(String[] tokens) {
        // FUNDS_IN|request_id|timestamp|client_id|amount
        if (tokens.length < 5) return;
        String reqId = tokens[1].trim();
        LocalDateTime ts = LocalDateTime.parse(tokens[2].trim(), TIMESTAMP_FORMATTER);
        String clientId = tokens[3].trim();
        BigDecimal amount = new BigDecimal(tokens[4].trim()).setScale(2, RoundingMode.HALF_UP);

        this.currentDate = ts.toLocalDate();
        orderExecutionService.executeFundsIn(reqId, ts, clientId, amount);
    }

    private void handleRetagCommand(String[] tokens) {
        // RETAG|timestamp|symbol|new_group
        if (tokens.length < 4) return;
        String symbol = tokens[2].trim().toUpperCase();
        PledgeGroup newGroup = PledgeGroup.fromString(tokens[3].trim());
        scripRepository.retagGroup(symbol, newGroup);
    }

    private void handlePriceUpdateCommand(String[] tokens) {
        // PRICE_UPDATE|timestamp|symbol|price
        if (tokens.length < 4) return;
        String symbol = tokens[2].trim().toUpperCase();
        BigDecimal price = new BigDecimal(tokens[3].trim()).setScale(2, RoundingMode.HALF_UP);
        stockPriceRepository.updateIntradayPrice(symbol, price);
    }

    private void handleSettleCommand(String[] tokens) {
        // SETTLE|date
        if (tokens.length < 2) return;
        LocalDate date = LocalDate.parse(tokens[1].trim());
        this.currentDate = date;
    }

    private void handleSquareOffCommand(String[] tokens) {
        // INTRADAY_SQUARE_OFF|timestamp
        stockPriceRepository.clearIntradayPrices();
    }

    private void handleEodCommand(String[] tokens) {
        // EOD|date
        if (tokens.length < 2) return;
        LocalDate date = LocalDate.parse(tokens[1].trim());
        this.currentDate = date;
        stockPriceRepository.clearIntradayPrices();
    }

    private void handlePrintReportCommand(String[] tokens) {
        // PRINT_REPORT|client_id|date
        if (tokens.length < 3) return;
        String clientId = tokens[1].trim();
        LocalDate date = LocalDate.parse(tokens[2].trim());
        String report = reportService.generateClientAccountReport(clientId, date);
        System.out.println(report);
    }

    /**
     * Executes an entire command file.
     */
    public void executeCommandFile(Path commandFilePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(commandFilePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                executeCommand(line);
            }
        }
    }

    public void executeCommandFile(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                executeCommand(line);
            }
        }
    }
}
