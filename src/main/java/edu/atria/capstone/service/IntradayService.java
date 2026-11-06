package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.LedgerRepository;
import edu.atria.capstone.repository.OrderRepository;
import edu.atria.capstone.repository.StockPriceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class IntradayService {
    private static final BigDecimal INTRADAY_MARGIN_RATE = new BigDecimal("0.20"); // 20%
    private static final BigDecimal MTM_BREACH_THRESHOLD_RATE = new BigDecimal("0.80"); // 80% threshold
    private static final BigDecimal AUTO_SQUARE_OFF_PENALTY = new BigDecimal("50.00"); // Rs 50 penalty

    private final ClientRepository clientRepository;
    private final StockPriceRepository stockPriceRepository;
    private final OrderRepository orderRepository;
    private final LedgerRepository ledgerRepository;
    private final ChargeCalculationService chargeService;
    private final MarginService marginService;

    // Map: ClientId -> (Symbol -> IntradayPosition)
    private final Map<String, Map<String, IntradayPosition>> openPositions = new LinkedHashMap<>();

    public IntradayService(ClientRepository clientRepository,
                           StockPriceRepository stockPriceRepository,
                           OrderRepository orderRepository,
                           LedgerRepository ledgerRepository,
                           ChargeCalculationService chargeService,
                           MarginService marginService) {
        this.clientRepository = clientRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.orderRepository = orderRepository;
        this.ledgerRepository = ledgerRepository;
        this.chargeService = chargeService;
        this.marginService = marginService;
    }

    /**
     * Records an executed intraday order and updates the open position state.
     */
    public void recordIntradayOrder(Order order) {
        String clientId = order.getClientId();
        String symbol = order.getSymbol().toUpperCase();

        Map<String, IntradayPosition> clientPositions = openPositions.computeIfAbsent(clientId, k -> new LinkedHashMap<>());
        IntradayPosition pos = clientPositions.get(symbol);

        if (pos == null) {
            // New position opened
            pos = new IntradayPosition(clientId, symbol, order.getQuantity(), order.getSide(),
                    order.getPrice(), order.getOrderId(), order.getTimestamp());
            clientPositions.put(symbol, pos);
        } else {
            // Existing position: add or reduce/reverse
            BigDecimal realizedPnl = pos.addTrade(order.getSide(), order.getQuantity(),
                    order.getPrice(), order.getOrderId(), order.getTimestamp());

            if (realizedPnl.compareTo(BigDecimal.ZERO) != 0) {
                // Post realized P&L to client ledger
                Client client = clientRepository.findById(clientId).orElse(null);
                if (client != null) {
                    if (realizedPnl.compareTo(BigDecimal.ZERO) > 0) {
                        client.creditCash(realizedPnl);
                        ledgerRepository.save(new LedgerEntry(
                                ledgerRepository.nextEntryId(),
                                order.getTimestamp(),
                                order.getTimestamp().toLocalDate(),
                                clientId,
                                "INTRADAY_REALIZED_PROFIT",
                                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                                realizedPnl,
                                client.getCashBalance(),
                                String.format("Intraday profit on %s (Ref: %s)", symbol, order.getOrderId()),
                                order.getOrderId()
                        ));
                    } else {
                        BigDecimal lossAmount = realizedPnl.abs();
                        client.debitCash(lossAmount);
                        ledgerRepository.save(new LedgerEntry(
                                ledgerRepository.nextEntryId(),
                                order.getTimestamp(),
                                order.getTimestamp().toLocalDate(),
                                clientId,
                                "INTRADAY_REALIZED_LOSS",
                                lossAmount,
                                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                                client.getCashBalance(),
                                String.format("Intraday loss on %s (Ref: %s)", symbol, order.getOrderId()),
                                order.getOrderId()
                        ));
                    }
                }
            }

            if (pos.isClosed()) {
                clientPositions.remove(symbol);
            }
        }
    }

    /**
     * Calculates total unrealized MTM profit/loss across all open intraday positions for a client.
     */
    public BigDecimal calculateClientTotalMtm(String clientId, LocalDate date) {
        Map<String, IntradayPosition> clientPositions = openPositions.get(clientId);
        if (clientPositions == null || clientPositions.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalMtm = BigDecimal.ZERO;
        for (IntradayPosition pos : clientPositions.values()) {
            if (!pos.isClosed()) {
                BigDecimal currentPrice = stockPriceRepository.getCurrentOrLatestPrice(date, pos.getSymbol())
                        .orElse(pos.getAvgCost());
                totalMtm = totalMtm.add(pos.calculateMtm(currentPrice));
            }
        }
        return totalMtm.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates total required margin across all active intraday positions for a client.
     */
    public BigDecimal calculateClientTotalRequiredMargin(String clientId, LocalDate date) {
        Map<String, IntradayPosition> clientPositions = openPositions.get(clientId);
        if (clientPositions == null || clientPositions.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalReq = BigDecimal.ZERO;
        for (IntradayPosition pos : clientPositions.values()) {
            if (!pos.isClosed()) {
                BigDecimal currentPrice = stockPriceRepository.getCurrentOrLatestPrice(date, pos.getSymbol())
                        .orElse(pos.getAvgCost());
                BigDecimal posValue = pos.getPositionValue(currentPrice);
                totalReq = totalReq.add(posValue.multiply(INTRADAY_MARGIN_RATE));
            }
        }
        return totalReq.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Real-time MTM evaluation triggered on PRICE_UPDATE.
     * If available equity drops below 80% of required margin, squares off largest position first.
     * Tie-breaker: earlier opening order_id squared off first (EC-08).
     */
    public void evaluateMtmAndSquareOff(String updatedSymbol, BigDecimal newPrice, LocalDateTime timestamp) {
        LocalDate date = timestamp.toLocalDate();

        // Check every client with open intraday positions
        List<String> clientIds = new ArrayList<>(openPositions.keySet());
        for (String clientId : clientIds) {
            Map<String, IntradayPosition> positions = openPositions.get(clientId);
            if (positions == null || positions.isEmpty()) continue;

            Client client = clientRepository.findById(clientId).orElse(null);
            if (client == null) continue;

            while (true) {
                positions = openPositions.get(clientId);
                if (positions == null || positions.isEmpty()) break;

                BigDecimal totalMtm = calculateClientTotalMtm(clientId, date);
                BigDecimal requiredMargin = calculateClientTotalRequiredMargin(clientId, date);

                if (requiredMargin.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal collateral = marginService.calculateCollateralValue(client, date);
                BigDecimal maxCollateral = requiredMargin.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal effectiveCollateral = collateral.min(maxCollateral);

                // Available Equity = Cash + Collateral + MTM P&L
                BigDecimal availableEquity = client.getCashBalance().add(effectiveCollateral).add(totalMtm);
                BigDecimal threshold = requiredMargin.multiply(MTM_BREACH_THRESHOLD_RATE).setScale(2, RoundingMode.HALF_UP);

                // Check breach: available equity < 80% of required margin
                if (availableEquity.compareTo(threshold) < 0 && totalMtm.compareTo(BigDecimal.ZERO) < 0) {
                    // Trigger MTM Auto Square-Off for largest position
                    IntradayPosition target = selectLargestPositionForSquareOff(positions, date);
                    if (target == null) break;

                    executeMtmPositionSquareOff(client, target, timestamp, newPrice);
                } else {
                    break;
                }
            }
        }
    }

    /**
     * Tie-breaker: selects the position with largest market value.
     * If tied for largest, selects the position opened earlier (lower order_id) (EC-08).
     */
    private IntradayPosition selectLargestPositionForSquareOff(Map<String, IntradayPosition> positions, LocalDate date) {
        IntradayPosition selected = null;
        BigDecimal largestValue = BigDecimal.ZERO;

        for (IntradayPosition pos : positions.values()) {
            if (pos.isClosed()) continue;

            BigDecimal currentPrice = stockPriceRepository.getCurrentOrLatestPrice(date, pos.getSymbol())
                    .orElse(pos.getAvgCost());
            BigDecimal val = pos.getPositionValue(currentPrice);

            if (selected == null || val.compareTo(largestValue) > 0) {
                selected = pos;
                largestValue = val;
            } else if (val.compareTo(largestValue) == 0) {
                // Tie-break: earlier order_id (lexicographically or earlier opening timestamp)
                if (pos.getOpeningOrderId().compareTo(selected.getOpeningOrderId()) < 0) {
                    selected = pos;
                }
            }
        }
        return selected;
    }

    private void executeMtmPositionSquareOff(Client client, IntradayPosition pos, LocalDateTime timestamp, BigDecimal currentPrice) {
        String sqRefId = String.format("SQ-%s-%s-%s", client.getClientId(), pos.getSymbol(), timestamp.toLocalDate());
        TransactionSide closeSide = pos.isLong() ? TransactionSide.SELL : TransactionSide.BUY;
        int closeQty = Math.abs(pos.getNetQuantity());

        BigDecimal execPrice = stockPriceRepository.getCurrentOrLatestPrice(timestamp.toLocalDate(), pos.getSymbol())
                .orElse(currentPrice);
        BigDecimal tradeVal = execPrice.multiply(BigDecimal.valueOf(closeQty)).setScale(2, RoundingMode.HALF_UP);

        ChargesBreakdown charges = chargeService.calculateAllCharges(ProductType.INTRADAY, closeSide, tradeVal);

        // Realized P&L
        BigDecimal pnl = pos.isLong()
                ? execPrice.subtract(pos.getAvgCost()).multiply(BigDecimal.valueOf(closeQty))
                : pos.getAvgCost().subtract(execPrice).multiply(BigDecimal.valueOf(closeQty));
        pnl = pnl.setScale(2, RoundingMode.HALF_UP);

        // Deduct charges and realized loss
        client.debitCash(charges.getTotalCharges());
        if (pnl.compareTo(BigDecimal.ZERO) < 0) {
            client.debitCash(pnl.abs());
        } else {
            client.creditCash(pnl);
        }

        // Ledger entry
        ledgerRepository.save(new LedgerEntry(
                ledgerRepository.nextEntryId(),
                timestamp,
                timestamp.toLocalDate(),
                client.getClientId(),
                "MTM_SQUARE_OFF",
                pnl.compareTo(BigDecimal.ZERO) < 0 ? pnl.abs().add(charges.getTotalCharges()) : charges.getTotalCharges(),
                pnl.compareTo(BigDecimal.ZERO) > 0 ? pnl : BigDecimal.ZERO,
                client.getCashBalance(),
                String.format("MTM margin call square-off %s %d %s @ %s (Loss: %s)", closeSide, closeQty, pos.getSymbol(), execPrice, pnl),
                sqRefId
        ));

        // Decision Record
        OrderDecisionRecord decRec = new OrderDecisionRecord(
                sqRefId,
                timestamp,
                client.getClientId(),
                pos.getSymbol(),
                ActionType.MTM_SQUARE_OFF,
                OrderDecision.ACCEPTED,
                "",
                closeQty,
                execPrice,
                tradeVal,
                BigDecimal.ZERO,
                client.getCashBalance()
        );
        orderRepository.addDecisionRecord(decRec);

        // Contract Note
        ContractNote note = new ContractNote(
                sqRefId,
                timestamp.toLocalDate(),
                client.getClientId(),
                pos.getSymbol(),
                closeSide,
                ProductType.INTRADAY,
                closeQty,
                execPrice,
                tradeVal,
                charges
        );
        orderRepository.addContractNote(note);

        // Remove position
        Map<String, IntradayPosition> clientPos = openPositions.get(client.getClientId());
        if (clientPos != null) {
            clientPos.remove(pos.getSymbol());
        }
    }

    /**
     * Executes auto square-off at 15:20 on remaining open intraday positions.
     * Applies Rs 50 penalty per position.
     */
    public void executeIntradayAutoSquareOff(LocalDateTime timestamp) {
        LocalDate date = timestamp.toLocalDate();

        for (Map.Entry<String, Map<String, IntradayPosition>> clientEntry : new ArrayList<>(openPositions.entrySet())) {
            String clientId = clientEntry.getKey();
            Client client = clientRepository.findById(clientId).orElse(null);
            if (client == null) continue;

            Map<String, IntradayPosition> positions = new ArrayList<>(clientEntry.getValue().values()).stream()
                    .collect(LinkedHashMap::new, (m, p) -> m.put(p.getSymbol(), p), Map::putAll);

            for (IntradayPosition pos : positions.values()) {
                if (pos.isClosed()) continue;

                String sqRefId = String.format("SQ-%s-%s-%s", clientId, pos.getSymbol(), date);
                TransactionSide closeSide = pos.isLong() ? TransactionSide.SELL : TransactionSide.BUY;
                int closeQty = Math.abs(pos.getNetQuantity());

                BigDecimal closePrice = stockPriceRepository.getClosePrice(date, pos.getSymbol())
                        .orElse(stockPriceRepository.getCurrentOrLatestPrice(date, pos.getSymbol()).orElse(pos.getAvgCost()));

                BigDecimal tradeVal = closePrice.multiply(BigDecimal.valueOf(closeQty)).setScale(2, RoundingMode.HALF_UP);

                // Charges with Rs 50 penalty
                ChargesBreakdown charges = chargeService.calculateAllCharges(
                        ProductType.INTRADAY, closeSide, tradeVal, AUTO_SQUARE_OFF_PENALTY);

                // Realized P&L
                BigDecimal pnl = pos.isLong()
                        ? closePrice.subtract(pos.getAvgCost()).multiply(BigDecimal.valueOf(closeQty))
                        : pos.getAvgCost().subtract(closePrice).multiply(BigDecimal.valueOf(closeQty));
                pnl = pnl.setScale(2, RoundingMode.HALF_UP);

                // Apply to client cash ledger: net cash impact = PnL - totalCharges (including Rs 50 penalty)
                if (pnl.compareTo(BigDecimal.ZERO) >= 0) {
                    client.creditCash(pnl);
                } else {
                    client.debitCash(pnl.abs());
                }
                client.debitCash(charges.getTotalCharges());

                // Ledger Entry
                ledgerRepository.save(new LedgerEntry(
                        ledgerRepository.nextEntryId(),
                        timestamp,
                        date,
                        clientId,
                        "INTRADAY_AUTO_SQUARE_OFF",
                        charges.getTotalCharges().add(pnl.compareTo(BigDecimal.ZERO) < 0 ? pnl.abs() : BigDecimal.ZERO),
                        pnl.compareTo(BigDecimal.ZERO) > 0 ? pnl : BigDecimal.ZERO,
                        client.getCashBalance(),
                        String.format("15:20 Auto Square-Off %s %d %s @ %s (Penalty: Rs 50.00, PnL: %s)", closeSide, closeQty, pos.getSymbol(), closePrice, pnl),
                        sqRefId
                ));

                // Decision Record
                OrderDecisionRecord decRec = new OrderDecisionRecord(
                        sqRefId,
                        timestamp,
                        clientId,
                        pos.getSymbol(),
                        ActionType.SQUARE_OFF,
                        OrderDecision.ACCEPTED,
                        "",
                        closeQty,
                        closePrice,
                        tradeVal,
                        BigDecimal.ZERO,
                        client.getCashBalance()
                );
                orderRepository.addDecisionRecord(decRec);

                // Contract Note
                ContractNote note = new ContractNote(
                        sqRefId,
                        date,
                        clientId,
                        pos.getSymbol(),
                        closeSide,
                        ProductType.INTRADAY,
                        closeQty,
                        closePrice,
                        tradeVal,
                        charges
                );
                orderRepository.addContractNote(note);
            }
        }

        // All positions are closed after 15:20
        openPositions.clear();
    }

    public Map<String, IntradayPosition> getClientPositions(String clientId) {
        Map<String, IntradayPosition> pos = openPositions.get(clientId);
        return pos == null ? Collections.emptyMap() : Collections.unmodifiableMap(pos);
    }

    public int getOpenPositionCount(String clientId) {
        Map<String, IntradayPosition> pos = openPositions.get(clientId);
        return pos == null ? 0 : pos.size();
    }

    public void clear() {
        openPositions.clear();
    }
}
