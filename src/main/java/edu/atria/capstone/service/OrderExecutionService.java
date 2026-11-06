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

    /**
     * Processes an order end-to-end:
     * 1. Calculates required margin & available margin
     * 2. Runs all business validations
     * 3. If valid: executes, updates ledger and holdings, generates contract note
     * 4. If invalid: rejects without modifying state, records rule IDs and reason
     */
    public Order processOrder(Order order, LocalDate tradeDate) {
        Client client = clientRepository.findById(order.getClientId()).orElse(null);

        BigDecimal tradeValue = marginService.calculateTradeValue(order.getQuantity(), order.getPrice());
        BigDecimal requiredMargin = marginService.calculateRequiredMargin(order.getProduct(), tradeValue);
        order.setRequiredMargin(requiredMargin);

        BigDecimal availableMargin = client != null
                ? marginService.calculateAvailableMargin(client, tradeDate, order.getProduct(), requiredMargin)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

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

        BigDecimal netAmount = charges.getNetAmount();
        String entryId = ledgerRepository.nextEntryId();

        if (order.getProduct() == ProductType.DELIVERY) {
            if (order.getSide() == TransactionSide.BUY) {
                // Debit cash ledger by net amount (Trade Value + Charges)
                client.debitCash(netAmount);
                // Credit stock holding (add shares and update weighted average price)
                client.addShares(order.getSymbol(), order.getQuantity(), order.getPrice());

                LedgerEntry entry = new LedgerEntry(
                        entryId,
                        order.getTimestamp(),
                        tradeDate,
                        client.getClientId(),
                        "DELIVERY_BUY",
                        netAmount,
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        client.getCashBalance(),
                        String.format("BUY %d %s @ %s (Charges: %s)",
                                order.getQuantity(), order.getSymbol(), order.getPrice(), charges.getTotalCharges()),
                        order.getOrderId()
                );
                ledgerRepository.save(entry);

            } else {
                // DELIVERY SELL
                // Credit cash ledger by net amount (Trade Value - Charges)
                client.creditCash(netAmount);
                // Deduct stock holding
                client.deductShares(order.getSymbol(), order.getQuantity());

                LedgerEntry entry = new LedgerEntry(
                        entryId,
                        order.getTimestamp(),
                        tradeDate,
                        client.getClientId(),
                        "DELIVERY_SELL",
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        netAmount,
                        client.getCashBalance(),
                        String.format("SELL %d %s @ %s (Charges: %s)",
                                order.getQuantity(), order.getSymbol(), order.getPrice(), charges.getTotalCharges()),
                        order.getOrderId()
                );
                ledgerRepository.save(entry);
            }
        } else {
            // INTRADAY
            if (order.getSide() == TransactionSide.BUY) {
                // Deduct charges immediately from ledger
                client.debitCash(charges.getTotalCharges());

                LedgerEntry entry = new LedgerEntry(
                        entryId,
                        order.getTimestamp(),
                        tradeDate,
                        client.getClientId(),
                        "INTRADAY_BUY_CHARGES",
                        charges.getTotalCharges(),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        client.getCashBalance(),
                        String.format("INTRADAY BUY %d %s @ %s charges",
                                order.getQuantity(), order.getSymbol(), order.getPrice()),
                        order.getOrderId()
                );
                ledgerRepository.save(entry);

            } else {
                // INTRADAY SELL
                client.debitCash(charges.getTotalCharges());

                LedgerEntry entry = new LedgerEntry(
                        entryId,
                        order.getTimestamp(),
                        tradeDate,
                        client.getClientId(),
                        "INTRADAY_SELL_CHARGES",
                        charges.getTotalCharges(),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        client.getCashBalance(),
                        String.format("INTRADAY SELL %d %s @ %s charges",
                                order.getQuantity(), order.getSymbol(), order.getPrice()),
                        order.getOrderId()
                );
                ledgerRepository.save(entry);
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
     * Charges Rs 20 pledge processing fee on success.
     */
    public boolean executePledge(String requestId, LocalDateTime timestamp, String clientId, String symbol, int quantity) {
        ValidationResult val = orderValidationService.validatePledge(clientId, symbol, quantity);
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

        // Rs 20 pledge charge
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
