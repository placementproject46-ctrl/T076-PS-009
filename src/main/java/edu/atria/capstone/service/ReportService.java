package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.LedgerRepository;
import edu.atria.capstone.repository.OrderRepository;
import edu.atria.capstone.repository.StockPriceRepository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ReportService {

    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;
    private final LedgerRepository ledgerRepository;
    private final StockPriceRepository stockPriceRepository;
    private final MarginService marginService;

    private final List<EodRecord> eodRecords = new ArrayList<>();

    public ReportService(ClientRepository clientRepository,
                         OrderRepository orderRepository,
                         LedgerRepository ledgerRepository,
                         StockPriceRepository stockPriceRepository,
                         MarginService marginService) {
        this.clientRepository = clientRepository;
        this.orderRepository = orderRepository;
        this.ledgerRepository = ledgerRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.marginService = marginService;
    }

    public void addEodRecord(EodRecord record) {
        if (record != null) {
            eodRecords.add(record);
        }
    }

    public List<EodRecord> getEodRecords() {
        return Collections.unmodifiableList(eodRecords);
    }

    public void writeOrderDecisionsCsv(Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("ref_id,timestamp,client_id,symbol,action,decision,rule_ids,quantity,price,trade_value,required_margin,available_margin");
            writer.newLine();
            for (OrderDecisionRecord record : orderRepository.getDecisionRecords()) {
                writer.write(record.toCsvRow());
                writer.newLine();
            }
        }
    }

    public void writeContractNotesCsv(Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("ref_id,trade_date,client_id,symbol,side,product,quantity,price,trade_value,brokerage,stt,exchange_charge,sebi_fee,stamp_duty,gst,penalty,total_charges,net_amount");
            writer.newLine();
            for (ContractNote note : orderRepository.getContractNotes()) {
                writer.write(note.toCsvRow());
                writer.newLine();
            }
        }
    }

    public void writeEodReportCsv(Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("date,client_id,ledger_balance,collateral_value,intraday_mtm,usable_margin,used_margin,interest_charged,open_intraday_positions");
            writer.newLine();
            for (EodRecord record : eodRecords) {
                writer.write(record.toCsvRow());
                writer.newLine();
            }
        }
    }

    public void writeHoldingsReportCsv(Path outputPath, LocalDate date) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("date,client_id,symbol,quantity,pledged_qty,close_price,collateral_value");
            writer.newLine();
            for (Client client : clientRepository.findAll()) {
                for (Map.Entry<String, Holding> entry : client.getHoldings().entrySet()) {
                    Holding h = entry.getValue();
                    if (h.getQuantity() > 0) {
                        BigDecimal closePrice = stockPriceRepository.getCurrentOrLatestPrice(date, h.getSymbol())
                                .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                        BigDecimal collateral = BigDecimal.ZERO;
                        if (h.getPledgedQty() > 0) {
                            collateral = closePrice.multiply(BigDecimal.valueOf(h.getPledgedQty()))
                                    .multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP);
                        }
                        writer.write(String.join(",",
                                date.toString(),
                                client.getClientId(),
                                h.getSymbol(),
                                String.valueOf(h.getQuantity()),
                                String.valueOf(h.getPledgedQty()),
                                closePrice.toPlainString(),
                                collateral.toPlainString()
                        ));
                        writer.newLine();
                    }
                }
            }
        }
    }

    /**
     * Formats a clean terminal dashboard of client account info.
     */
    public String generateClientAccountReport(String clientId, LocalDate asOfDate) {
        Client client = clientRepository.findById(clientId).orElse(null);
        if (client == null) {
            return "Client not found: " + clientId;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================================\n");
        sb.append(String.format("   CLIENT ACCOUNT STATEMENT | ID: %-8s | NAME: %s\n", client.getClientId(), client.getClientName()));
        sb.append(String.format("   PAN: %-12s | MOBILE: %-12s | AS OF: %s\n", client.getPan(), client.getMobile(), asOfDate));
        sb.append("========================================================================================\n");
        sb.append(String.format(" Ledger Cash Balance     : Rs %12s\n", client.getCashBalance().toPlainString()));

        BigDecimal collateralVal = marginService.calculateCollateralValue(client, asOfDate);
        sb.append(String.format(" Pledged Collateral Value: Rs %12s\n", collateralVal.toPlainString()));
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(String.format(" %-12s | %8s | %8s | %8s | %10s | %10s | %12s\n",
                "SYMBOL", "TOTAL", "FREE", "PLEDGED", "AVG COST", "LTP", "MKT VALUE"));
        sb.append("----------------------------------------------------------------------------------------\n");

        BigDecimal totalHoldingValue = BigDecimal.ZERO;
        for (Holding h : client.getHoldings().values()) {
            BigDecimal ltp = stockPriceRepository.getCurrentOrLatestPrice(asOfDate, h.getSymbol())
                    .orElse(h.getAvgCost()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal mktVal = ltp.multiply(BigDecimal.valueOf(h.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            totalHoldingValue = totalHoldingValue.add(mktVal);

            sb.append(String.format(" %-12s | %8d | %8d | %8d | %10s | %10s | %12s\n",
                    h.getSymbol(),
                    h.getQuantity(),
                    h.getFreeQty(),
                    h.getPledgedQty(),
                    h.getAvgCost().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    ltp.toPlainString(),
                    mktVal.toPlainString()));
        }
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(String.format(" Total Demat Portfolio Value: Rs %12s\n", totalHoldingValue.toPlainString()));
        BigDecimal totalNetWorth = client.getCashBalance().add(totalHoldingValue).setScale(2, RoundingMode.HALF_UP);
        sb.append(String.format(" Total Client Net Worth     : Rs %12s\n", totalNetWorth.toPlainString()));
        sb.append("========================================================================================\n");

        return sb.toString();
    }

    public void clear() {
        eodRecords.clear();
    }
}
