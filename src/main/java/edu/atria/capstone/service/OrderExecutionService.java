package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.LedgerRepository;
import edu.atria.capstone.repository.OrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class OrderExecutionService {

    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;
    private final LedgerRepository ledgerRepository;
    private final ChargeCalculationService chargeCalculationService;
    private final MarginService marginService;
    private final OrderValidationService orderValidationService;
    private SettlementService settlementService;
    private IntradayService intradayService;

    public OrderExecutionService(ClientRepository clientRepository,
                                 OrderRepository orderRepository,
                                 LedgerRepository ledgerRepository,
                                 ChargeCalculationService chargeCalculationService,
                                 MarginService marginService,
                                 OrderValidationService orderValidationService) {
        this.clientRepository = clientRepository;
        this.orderRepository = orderRepository;
        this.ledgerRepository = ledgerRepository;
        this.chargeCalculationService = chargeCalculationService;
        this.marginService = marginService;
        this.orderValidationService = orderValidationService;
    }

    public void setSettlementService(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    public void setIntradayService(IntradayService intradayService) {
        this.intradayService = intradayService;
    }

    /**
     * Processes an order end-to-end:
     * 1. Evaluates all business validations according to strict precedence
     * 2. If valid: executes state update, queues delivery or tracks intraday, generates contract note
     * 3. If invalid: rejects without modifying state, records rule IDs and reason
     */
    public Order processOrder(Order order, LocalDate tradeDate) {
        Client client = clientRepository.findById(order.getClientId()).orElse(null);

        BigDecimal tradeValue = marginService.calculateTradeValue(order.getQuantity(), order.getPrice());
        BigDecimal requiredMargin = marginService.calculateRequiredMargin(order.getProduct(), tradeValue);
        order.setRequiredMargin(requiredMargin);

        BigDecimal availableMargin = client != null
                ? marginService.calculateAvailableMargin(client, tradeDate, order.getProduct(), requiredMargin)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (settlementService != null && client != null && order.getSide() == TransactionSide.BUY) {
            BigDecimal sameDayCredit = settlementService.getAvailableSameDaySellProceeds(client.getClientId());
            availableMargin = availableMargin.add(sameDayCredit).setScale(2, RoundingMode.HALF_UP);
        }

        // Run all validations
        ValidationResult valResult = orderValidationService.validateOrder(order, tradeDate);
        ActionType action = order.getSide() == TransactionSide.BUY ? ActionType.BUY : ActionType.SELL;

        if (!valResult.isValid()) {
            // ORDER REJECTED
            order.setDecision(OrderDecision.REJECTED);
            order.setRuleIds(valResult.getFailures().stream().map(ValidationFailure::getRuleId).toList());
            order.setRejectionReason(valResult.getCombinedMessage());

            OrderDecisionRecord decisionRecord = new OrderDecisionRecord(
                    order.getOrderId(),
                    order.getTimestamp(),
                    order.getClientId(),
                    order.getSymbol(),
                    action,
                    OrderDecision.REJECTED,
                    valResult.getRuleIdsString(),
                    order.getQuantity(),
                    order.getPrice(),
                    tradeValue,
                    requiredMargin,
                    availableMargin
            );
            orderRepository.addDecisionRecord(decisionRecord);
            orderRepository.save(order);
            return order;
        }

        // ORDER ACCEPTED & EXECUTED
        order.setDecision(OrderDecision.ACCEPTED);
        ChargesBreakdown charges = chargeCalculationService.calculateAllCharges(
                order.getProduct(), order.getSide(), tradeValue);
        order.setChargesBreakdown(charges);

        // Execute state update on client account
        executeOrderStateUpdate(order, client, tradeDate, charges);

        // Record contract note
        ContractNote note = new ContractNote(
                order.getOrderId(),
                tradeDate,
                order.getClientId(),
                order.getSymbol(),
                order.getSide(),
                order.getProduct(),
                order.getQuantity(),
                order.getPrice(),
                tradeValue,
                charges
        );
        orderRepository.addContractNote(note);

        // Record decision record
        OrderDecisionRecord decisionRecord = new OrderDecisionRecord(
                order.getOrderId(),
                order.getTimestamp(),
                order.getClientId(),
                order.getSymbol(),
                action,
                OrderDecision.ACCEPTED,
                "",
                order.getQuantity(),
                order.getPrice(),
                tradeValue,
                requiredMargin,
                availableMargin
        );
        orderRepository.addDecisionRecord(decisionRecord);
        orderRepository.save(order);

        return order;
    }

    private void executeOrderStateUpdate(Order order, Client client, LocalDate tradeDate, ChargesBreakdown charges) {
        if (client == null) return;

        if (order.getProduct() == ProductType.DELIVERY) {
            // Queue into T+1 settlement service
            if (settlementService != null) {
                settlementService.queueDeliveryOrder(order, tradeDate);
                if (order.getSide() == TransactionSide.BUY) {
                    // If using same-day sell proceeds, record the usage
                    BigDecimal usableCredit = settlementService.getAvailableSameDaySellProceeds(client.getClientId());
                    if (usableCredit.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal usedFromCredit = order.getTradeValue().min(usableCredit);
                        settlementService.useSameDaySellProceeds(client.getClientId(), usedFromCredit);
                    }
                }
            } else {
                // Immediate settlement fallback (for standalone tests without settlement engine)
                BigDecimal netAmount = charges.getNetAmount();
                String entryId = ledgerRepository.nextEntryId();
                if (order.getSide() == TransactionSide.BUY) {
                    client.debitCash(netAmount);
                    client.addShares(order.getSymbol(), order.getQuantity(), order.getPrice());
                    ledgerRepository.save(new LedgerEntry(
                            entryId, order.getTimestamp(), tradeDate, client.getClientId(), "DELIVERY_BUY",
                            netAmount, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), client.getCashBalance(),
                            String.format("BUY %d %s @ %s (Charges: %s)", order.getQuantity(), order.getSymbol(), order.getPrice(), charges.getTotalCharges()),
                            order.getOrderId()
                    ));
                } else {
                    client.creditCash(netAmount);
                    client.deductShares(order.getSymbol(), order.getQuantity());
                    ledgerRepository.save(new LedgerEntry(
                            entryId, order.getTimestamp(), tradeDate, client.getClientId(), "DELIVERY_SELL",
                            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), netAmount, client.getCashBalance(),
                            String.format("SELL %d %s @ %s (Charges: %s)", order.getQuantity(), order.getSymbol(), order.getPrice(), charges.getTotalCharges()),
                            order.getOrderId()
                    ));
                }
            }
        } else {
            // INTRADAY
            // Deduct statutory charges immediately from cash ledger
            client.debitCash(charges.getTotalCharges());
            String entryId = ledgerRepository.nextEntryId();
            String entryType = order.getSide() == TransactionSide.BUY ? "INTRADAY_BUY_CHARGES" : "INTRADAY_SELL_CHARGES";
            ledgerRepository.save(new LedgerEntry(
                    entryId, order.getTimestamp(), tradeDate, client.getClientId(), entryType,
                    charges.getTotalCharges(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), client.getCashBalance(),
                    String.format("INTRADAY %s %d %s @ %s charges", order.getSide(), order.getQuantity(), order.getSymbol(), order.getPrice()),
                    order.getOrderId()
            ));

            // Record into Intraday position manager
            if (intradayService != null) {
                intradayService.recordIntradayOrder(order);
            }
        }
    }

    /**
     * Executes manual funds deposit (FUNDS_IN).
     */
    public void executeFundsIn(String requestId, LocalDateTime timestamp, String clientId, BigDecimal amount) {
        Client client = clientRepository.findById(clientId).orElse(null);
        if (client == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        client.creditCash(amount);
        LedgerEntry entry = new LedgerEntry(
                ledgerRepository.nextEntryId(),
                timestamp,
                timestamp.toLocalDate(),
                clientId,
                "FUNDS_IN",
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                amount.setScale(2, RoundingMode.HALF_UP),
                client.getCashBalance(),
                "Client funds deposit: " + amount,
                requestId
        );
        ledgerRepository.save(entry);
    }

    /**
     * Executes PLEDGE request.
     * Checks holiday validation (EC-07).
     * Charges Rs 20 pledge processing fee on success.
     */
    public boolean executePledge(String requestId, LocalDateTime timestamp, String clientId, String symbol, int quantity) {
        ValidationResult val = orderValidationService.validatePledge(clientId, symbol, quantity, timestamp.toLocalDate());
        Client client = clientRepository.findById(clientId).orElse(null);

        BigDecimal availableMargin = client != null
                ? marginService.calculateAvailableMargin(client, timestamp.toLocalDate(), ProductType.INTRADAY, BigDecimal.ZERO)
                : BigDecimal.ZERO;

        if (!val.isValid()) {
            OrderDecisionRecord rec = new OrderDecisionRecord(
                    requestId,
                    timestamp,
                    clientId,
                    symbol,
                    ActionType.PLEDGE,
                    OrderDecision.REJECTED,
                    val.getRuleIdsString(),
                    quantity,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    availableMargin
            );
            orderRepository.addDecisionRecord(rec);
            return false;
        }

        // Apply pledge
        client.pledgeShares(symbol, quantity);

        // Rs 20 pledge fee
        BigDecimal pledgeFee = new BigDecimal("20.00");
        client.debitCash(pledgeFee);
        LedgerEntry entry = new LedgerEntry(
                ledgerRepository.nextEntryId(),
                timestamp,
                timestamp.toLocalDate(),
                clientId,
                "PLEDGE_FEE",
                pledgeFee,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                client.getCashBalance(),
                String.format("Pledge processing fee for %d shares of %s", quantity, symbol),
                requestId
        );
        ledgerRepository.save(entry);

        OrderDecisionRecord rec = new OrderDecisionRecord(
                requestId,
                timestamp,
                clientId,
                symbol,
                ActionType.PLEDGE,
                OrderDecision.ACCEPTED,
                "",
                quantity,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                availableMargin
        );
        orderRepository.addDecisionRecord(rec);
        return true;
    }

    /**
     * Executes UNPLEDGE request.
     */
    public boolean executeUnpledge(String requestId, LocalDateTime timestamp, String clientId, String symbol, int quantity) {
        ValidationResult val = orderValidationService.validateUnpledge(clientId, symbol, quantity);
        Client client = clientRepository.findById(clientId).orElse(null);

        BigDecimal availableMargin = client != null
                ? marginService.calculateAvailableMargin(client, timestamp.toLocalDate(), ProductType.INTRADAY, BigDecimal.ZERO)
                : BigDecimal.ZERO;

        if (!val.isValid()) {
            OrderDecisionRecord rec = new OrderDecisionRecord(
                    requestId,
                    timestamp,
                    clientId,
                    symbol,
                    ActionType.UNPLEDGE,
                    OrderDecision.REJECTED,
                    val.getRuleIdsString(),
                    quantity,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    availableMargin
            );
            orderRepository.addDecisionRecord(rec);
            return false;
        }

        client.unpledgeShares(symbol, quantity);

        OrderDecisionRecord rec = new OrderDecisionRecord(
                requestId,
                timestamp,
                clientId,
                symbol,
                ActionType.UNPLEDGE,
                OrderDecision.ACCEPTED,
                "",
                quantity,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                availableMargin
        );
        orderRepository.addDecisionRecord(rec);
        return true;
    }
}
