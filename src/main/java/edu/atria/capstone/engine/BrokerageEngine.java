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

    // Week 3 Specialized Engines
    private final SettlementService settlementService;
    private final IntradayService intradayService;
    private final InterestCalculationService interestService;
    private final AuctionSettlementService auctionService;
    private final InputValidationService inputValidationService;

    private LocalDate currentDate = LocalDate.of(2026, 10, 1);

    public BrokerageEngine() {
        this.marginService = new MarginService(scripRepository, stockPriceRepository);
        this.settlementService = new SettlementService(clientRepository, holidayRepository, ledgerRepository);
        this.intradayService = new IntradayService(clientRepository, stockPriceRepository, orderRepository, ledgerRepository, chargeCalculationService, marginService);
        this.interestService = new InterestCalculationService(clientRepository, ledgerRepository);
        this.auctionService = new AuctionSettlementService(clientRepository, stockPriceRepository, ledgerRepository);
        this.inputValidationService = new InputValidationService(clientRepository, scripRepository, holidayRepository);

        this.orderValidationService = new OrderValidationService(
                clientRepository, scripRepository, stockPriceRepository, holidayRepository, marginService, chargeCalculationService, settlementService);
        this.orderExecutionService = new OrderExecutionService(
                clientRepository, orderRepository, ledgerRepository, chargeCalculationService, marginService, orderValidationService);
        this.orderExecutionService.setSettlementService(settlementService);
        this.orderExecutionService.setIntradayService(intradayService);

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

    public SettlementService getSettlementService() {
        return settlementService;
    }

    public IntradayService getIntradayService() {
        return intradayService;
    }

    public InterestCalculationService getInterestService() {
        return interestService;
    }

    public AuctionSettlementService getAuctionService() {
        return auctionService;
    }

    public InputValidationService getInputValidationService() {
        return inputValidationService;
    }

    public LocalDate getCurrentDate() {
        return currentDate;
    }

    public void setCurrentDate(LocalDate currentDate) {
        this.currentDate = currentDate;
    }

    /**
     * Executes a command string with input validation.
     */
    public void executeCommand(String commandLine) {
        if (commandLine == null) return;
        String line = commandLine.trim();
        if (line.isEmpty() || line.startsWith("#")) return;

        // Phase 3: Input validation check before execution
        ValidationResult valRes = inputValidationService.validateCommandLine(line);
        if (!valRes.isValid()) {
            System.err.println("Input validation rejection: " + valRes.getCombinedMessage());
            return;
        }

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
        if (tokens.length < 5) return;
        String reqId = tokens[1].trim();
        LocalDateTime ts = LocalDateTime.parse(tokens[2].trim(), TIMESTAMP_FORMATTER);
        String clientId = tokens[3].trim();
        BigDecimal amount = new BigDecimal(tokens[4].trim()).setScale(2, RoundingMode.HALF_UP);

        this.currentDate = ts.toLocalDate();
        orderExecutionService.executeFundsIn(reqId, ts, clientId, amount);
    }

    private void handleRetagCommand(String[] tokens) {
        if (tokens.length < 4) return;
        String symbol = tokens[2].trim().toUpperCase();
        PledgeGroup newGroup = PledgeGroup.fromString(tokens[3].trim());
        scripRepository.retagGroup(symbol, newGroup);
    }

    private void handlePriceUpdateCommand(String[] tokens) {
        if (tokens.length < 4) return;
        LocalDateTime ts = LocalDateTime.parse(tokens[1].trim(), TIMESTAMP_FORMATTER);
        String symbol = tokens[2].trim().toUpperCase();
        BigDecimal price = new BigDecimal(tokens[3].trim()).setScale(2, RoundingMode.HALF_UP);

        this.currentDate = ts.toLocalDate();
        stockPriceRepository.updateIntradayPrice(symbol, price);

        // Real-time MTM evaluation & margin call check
        intradayService.evaluateMtmAndSquareOff(symbol, price, ts);
    }

    private void handleSettleCommand(String[] tokens) {
        if (tokens.length < 2) return;
        LocalDate date = LocalDate.parse(tokens[1].trim());
        this.currentDate = date;

        // Settle all T+1 delivery obligations falling on this date
        settlementService.executeSettlement(date);
    }

    private void handleSquareOffCommand(String[] tokens) {
        if (tokens.length < 2) return;
        LocalDateTime ts = LocalDateTime.parse(tokens[1].trim(), TIMESTAMP_FORMATTER);
        this.currentDate = ts.toLocalDate();

        // 15:20 Auto square-off on open intraday positions with Rs 50 penalty
        intradayService.executeIntradayAutoSquareOff(ts);
        stockPriceRepository.clearIntradayPrices();
    }

    private void handleEodCommand(String[] tokens) {
        if (tokens.length < 2) return;
        LocalDate date = LocalDate.parse(tokens[1].trim());
        this.currentDate = date;

        // Perform EOD processing for every registered client
        for (Client client : clientRepository.findAll()) {
            String clientId = client.getClientId();

            // 1. Negative ledger balance interest (0.05% starting from next day)
            BigDecimal interestCharged = interestService.calculateAndApplyDailyInterest(clientId, date);

            // 2. Compute EOD metrics
            BigDecimal ledgerBalance = client.getCashBalance();
            BigDecimal collateralValue = marginService.calculateCollateralValue(client, date);
            BigDecimal intradayMtm = intradayService.calculateClientTotalMtm(clientId, date);
            BigDecimal usedMargin = intradayService.calculateClientTotalRequiredMargin(clientId, date);
            BigDecimal usableMargin = marginService.calculateAvailableMargin(client, date, ProductType.INTRADAY, usedMargin);
            int openPositions = intradayService.getOpenPositionCount(clientId);

            EodRecord eodRecord = new EodRecord(
                    date,
                    clientId,
                    ledgerBalance,
                    collateralValue,
                    intradayMtm,
                    usableMargin,
                    usedMargin,
                    interestCharged,
                    openPositions
            );
            reportService.addEodRecord(eodRecord);
        }

        stockPriceRepository.clearIntradayPrices();
    }

    private void handlePrintReportCommand(String[] tokens) {
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
