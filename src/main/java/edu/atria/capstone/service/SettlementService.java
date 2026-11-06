package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.HolidayRepository;
import edu.atria.capstone.repository.LedgerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class SettlementService {
    private static final BigDecimal SAME_DAY_SELL_PROCEEDS_USABILITY_RATE = new BigDecimal("0.80"); // 80% on T

    private final ClientRepository clientRepository;
    private final HolidayRepository holidayRepository;
    private final LedgerRepository ledgerRepository;

    private final List<SettlementObligation> obligations = new ArrayList<>();
    private final AtomicLong obligationSequence = new AtomicLong(1);

    // Track delivery shares sold today pending settlement: (clientId#symbol -> soldQty)
    private final Map<String, Integer> pendingSoldQuantities = new HashMap<>();

    // Track 80% delivery sale proceeds available on trade date: (clientId -> BigDecimal)
    private final Map<String, BigDecimal> sameDaySellProceedsAvailable = new HashMap<>();
    // Track how much of same-day credit has been used by new buys today: (clientId -> BigDecimal)
    private final Map<String, BigDecimal> sameDaySellProceedsUsed = new HashMap<>();

    public SettlementService(ClientRepository clientRepository,
                             HolidayRepository holidayRepository,
                             LedgerRepository ledgerRepository) {
        this.clientRepository = clientRepository;
        this.holidayRepository = holidayRepository;
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * Calculates the T+1 settlement date, skipping weekends and market holidays.
     */
    public LocalDate calculateSettlementDate(LocalDate tradeDate) {
        if (tradeDate == null) return null;
        return holidayRepository.getNextBusinessDay(tradeDate);
    }

    /**
     * Records a delivery order into the T+1 settlement queue on trade date T.
     */
    public SettlementObligation queueDeliveryOrder(Order order, LocalDate tradeDate) {
        LocalDate settlementDate = calculateSettlementDate(tradeDate);
        String oblId = String.format("OBL-%05d", obligationSequence.getAndIncrement());

        SettlementObligation obligation = new SettlementObligation(
                oblId,
                order.getOrderId(),
                tradeDate,
                settlementDate,
                order.getClientId(),
                order.getSymbol(),
                order.getSide(),
                order.getQuantity(),
                order.getPrice(),
                order.getTradeValue(),
                order.getChargesBreakdown()
        );
        obligations.add(obligation);

        if (order.getSide() == TransactionSide.SELL) {
            // Reserve sold shares: Free holdings = Total - Pledged - SoldToday
            String key = order.getClientId() + "#" + order.getSymbol().toUpperCase();
            pendingSoldQuantities.put(key, pendingSoldQuantities.getOrDefault(key, 0) + order.getQuantity());

            // 80% of gross sale proceeds available immediately for new buys on trade date
            BigDecimal usableCredit = order.getTradeValue().multiply(SAME_DAY_SELL_PROCEEDS_USABILITY_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
            sameDaySellProceedsAvailable.put(order.getClientId(),
                    sameDaySellProceedsAvailable.getOrDefault(order.getClientId(), BigDecimal.ZERO).add(usableCredit));
        }

        return obligation;
    }

    /**
     * Gets pending delivery sold quantity on trade date T.
     */
    public int getPendingSoldQuantity(String clientId, String symbol) {
        String key = clientId + "#" + symbol.toUpperCase();
        return pendingSoldQuantities.getOrDefault(key, 0);
    }

    /**
     * Free holdings available to sell = Total Holdings - Pledged - SoldToday.
     */
    public int getAvailableFreeHoldingQuantity(Client client, String symbol) {
        if (client == null || symbol == null) return 0;
        int settledFree = client.getFreeHoldingQuantity(symbol);
        int soldToday = getPendingSoldQuantity(client.getClientId(), symbol);
        return Math.max(0, settledFree - soldToday);
    }

    /**
     * Gets remaining unused 80% same-day delivery sale proceeds available on trade date T.
     */
    public BigDecimal getAvailableSameDaySellProceeds(String clientId) {
        BigDecimal total80 = sameDaySellProceedsAvailable.getOrDefault(clientId, BigDecimal.ZERO);
        BigDecimal used = sameDaySellProceedsUsed.getOrDefault(clientId, BigDecimal.ZERO);
        return total80.subtract(used).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Blocks portion of same-day sell credit when a new buy is executed on trade date T.
     */
    public void useSameDaySellProceeds(String clientId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal used = sameDaySellProceedsUsed.getOrDefault(clientId, BigDecimal.ZERO);
        sameDaySellProceedsUsed.put(clientId, used.add(amount).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Executes 08:00 T+1 settlement on the given settlement date.
     */
    public List<SettlementObligation> executeSettlement(LocalDate settlementDate) {
        List<SettlementObligation> settledThisBatch = new ArrayList<>();

        for (SettlementObligation obl : obligations) {
            if (!obl.isSettled() && settlementDate.equals(obl.getSettlementDate())) {
                Client client = clientRepository.findById(obl.getClientId()).orElse(null);
                if (client != null) {
                    if (obl.getSide() == TransactionSide.BUY) {
                        // Settle BUY: debit ledger cash by netAmount (Trade Value + Charges), credit demat shares
                        client.debitCash(obl.getNetAmount());
                        client.addShares(obl.getSymbol(), obl.getQuantity(), obl.getPrice());

                        LedgerEntry entry = new LedgerEntry(
                                ledgerRepository.nextEntryId(),
                                LocalDateTime.of(settlementDate, java.time.LocalTime.of(8, 0, 0)),
                                settlementDate,
                                client.getClientId(),
                                "SETTLEMENT_DELIVERY_BUY",
                                obl.getNetAmount(),
                                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                                client.getCashBalance(),
                                String.format("Settled BUY %d %s (Ref: %s)", obl.getQuantity(), obl.getSymbol(), obl.getOrderId()),
                                obl.getObligationId()
                        );
                        ledgerRepository.save(entry);

                    } else {
                        // Settle SELL: credit ledger cash by netAmount (Trade Value - Charges), deduct demat shares
                        client.creditCash(obl.getNetAmount());
                        client.deductShares(obl.getSymbol(), obl.getQuantity());

                        // Remove from pending sold quantity
                        String key = obl.getClientId() + "#" + obl.getSymbol().toUpperCase();
                        int remainingPending = Math.max(0, pendingSoldQuantities.getOrDefault(key, 0) - obl.getQuantity());
                        if (remainingPending == 0) pendingSoldQuantities.remove(key);
                        else pendingSoldQuantities.put(key, remainingPending);

                        LedgerEntry entry = new LedgerEntry(
                                ledgerRepository.nextEntryId(),
                                LocalDateTime.of(settlementDate, java.time.LocalTime.of(8, 0, 0)),
                                settlementDate,
                                client.getClientId(),
                                "SETTLEMENT_DELIVERY_SELL",
                                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                                obl.getNetAmount(),
                                client.getCashBalance(),
                                String.format("Settled SELL %d %s (Ref: %s)", obl.getQuantity(), obl.getSymbol(), obl.getOrderId()),
                                obl.getObligationId()
                        );
                        ledgerRepository.save(entry);
                    }
                }
                obl.setSettled(true);
                settledThisBatch.add(obl);
            }
        }

        // On settlement date, reset same-day proceeds tracking for settled trades
        sameDaySellProceedsAvailable.clear();
        sameDaySellProceedsUsed.clear();

        return settledThisBatch;
    }

    public List<SettlementObligation> getObligations() {
        return Collections.unmodifiableList(obligations);
    }

    public void clear() {
        obligations.clear();
        pendingSoldQuantities.clear();
        sameDaySellProceedsAvailable.clear();
        sameDaySellProceedsUsed.clear();
        obligationSequence.set(1);
    }
}
