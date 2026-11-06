package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.HolidayRepository;
import edu.atria.capstone.repository.ScripRepository;
import edu.atria.capstone.repository.StockPriceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

public class OrderValidationService {

    // Business Rule IDs
    public static final String BR_VAL_CLIENT_EXISTS = "BR_VAL_001";
    public static final String BR_VAL_SYMBOL_EXISTS = "BR_VAL_002";
    public static final String BR_VAL_POSITIVE_QTY = "BR_VAL_003";
    public static final String BR_VAL_POSITIVE_PRICE = "BR_VAL_004";
    public static final String BR_VAL_AVAILABLE_FUNDS = "BR_VAL_005";
    public static final String BR_VAL_HOLDINGS_FREE = "BR_VAL_006";
    public static final String BR_VAL_PRICE_BAND = "BR_VAL_007";
    public static final String BR_VAL_PLEDGE_ELIGIBILITY = "BR_VAL_008";
    public static final String BR_VAL_UNPLEDGE_QTY = "BR_VAL_009";
    public static final String BR_VAL_MARKET_HOLIDAY = "BR_VAL_010";

    private static final BigDecimal PRICE_BAND_LOWER_FACTOR = new BigDecimal("0.90"); // -10%
    private static final BigDecimal PRICE_BAND_UPPER_FACTOR = new BigDecimal("1.10"); // +10%

    private final ClientRepository clientRepository;
    private final ScripRepository scripRepository;
    private final StockPriceRepository stockPriceRepository;
    private final HolidayRepository holidayRepository;
    private final MarginService marginService;
    private final ChargeCalculationService chargeCalculationService;
    private SettlementService settlementService;

    public OrderValidationService(ClientRepository clientRepository,
                                  ScripRepository scripRepository,
                                  StockPriceRepository stockPriceRepository,
                                  MarginService marginService,
                                  ChargeCalculationService chargeCalculationService) {
        this(clientRepository, scripRepository, stockPriceRepository, null, marginService, chargeCalculationService, null);
    }

    public OrderValidationService(ClientRepository clientRepository,
                                  ScripRepository scripRepository,
                                  StockPriceRepository stockPriceRepository,
                                  HolidayRepository holidayRepository,
                                  MarginService marginService,
                                  ChargeCalculationService chargeCalculationService,
                                  SettlementService settlementService) {
        this.clientRepository = clientRepository;
        this.scripRepository = scripRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.holidayRepository = holidayRepository;
        this.marginService = marginService;
        this.chargeCalculationService = chargeCalculationService;
        this.settlementService = settlementService;
    }

    public void setSettlementService(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /**
     * Evaluates all applicable validations for an order in strict precedence:
     * 1. Holdings check (for Delivery Sell)
     * 2. Margin / Available Funds check
     * 3. Price-band check (+/- 10%)
     * 4. Execution decision
     *
     * Reports ALL failures together rather than stopping after first failure.
     */
    public ValidationResult validateOrder(Order order, LocalDate tradeDate) {
        ValidationResult result = new ValidationResult();

        // Basic structural validations
        Optional<Client> clientOpt = clientRepository.findById(order.getClientId());
        if (clientOpt.isEmpty()) {
            result.addFailure(BR_VAL_CLIENT_EXISTS, "Client does not exist: " + order.getClientId());
        }

        Optional<Scrip> scripOpt = scripRepository.findBySymbol(order.getSymbol());
        if (scripOpt.isEmpty()) {
            result.addFailure(BR_VAL_SYMBOL_EXISTS, "Stock symbol not found in scrip master: " + order.getSymbol());
        }

        if (order.getQuantity() <= 0) {
            result.addFailure(BR_VAL_POSITIVE_QTY,
                    "Quantity must be a positive integer greater than zero. Received: " + order.getQuantity());
        }

        if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            result.addFailure(BR_VAL_POSITIVE_PRICE,
                    "Price must be greater than zero. Received: " + order.getPrice());
        }

        // PRECEDENCE STEP 1: Holdings check (for DELIVERY SELL)
        if (order.getSide() == TransactionSide.SELL && order.getProduct() == ProductType.DELIVERY) {
            if (clientOpt.isPresent()) {
                Client client = clientOpt.get();
                int freeShares = settlementService != null
                        ? settlementService.getAvailableFreeHoldingQuantity(client, order.getSymbol())
                        : client.getFreeHoldingQuantity(order.getSymbol());

                if (freeShares < order.getQuantity()) {
                    result.addFailure(BR_VAL_HOLDINGS_FREE,
                            String.format("Insufficient free holdings for Delivery Sell. Required: %d, Free Available: %d (Total held: %d, Pledged: %d)",
                                    order.getQuantity(), freeShares,
                                    client.getTotalHoldingQuantity(order.getSymbol()),
                                    client.getPledgedQuantity(order.getSymbol())));
                }
            }
        }

        // PRECEDENCE STEP 2: Margin check
        if (order.getSide() == TransactionSide.BUY && clientOpt.isPresent() && order.getQuantity() > 0 && order.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            Client client = clientOpt.get();
            BigDecimal tradeValue = marginService.calculateTradeValue(order.getQuantity(), order.getPrice());
            BigDecimal requiredMargin = marginService.calculateRequiredMargin(order.getProduct(), tradeValue);

            BigDecimal availableMargin = marginService.calculateAvailableMargin(client, tradeDate, order.getProduct(), requiredMargin);

            // Add 80% same-day delivery sale proceeds credit if applicable
            if (settlementService != null) {
                BigDecimal sameDayCredit = settlementService.getAvailableSameDaySellProceeds(client.getClientId());
                availableMargin = availableMargin.add(sameDayCredit).setScale(2, RoundingMode.HALF_UP);
            }

            if (availableMargin.compareTo(requiredMargin) < 0) {
                result.addFailure(BR_VAL_AVAILABLE_FUNDS,
                        String.format("Insufficient funds/margin. Required Margin: %s (Trade Value: %s), Available Margin: %s",
                                requiredMargin, tradeValue, availableMargin));
            }
        } else if (order.getSide() == TransactionSide.SELL && order.getProduct() == ProductType.INTRADAY && clientOpt.isPresent() && order.getQuantity() > 0 && order.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            // Intraday Short Selling: requires 20% margin
            Client client = clientOpt.get();
            BigDecimal tradeValue = marginService.calculateTradeValue(order.getQuantity(), order.getPrice());
            BigDecimal requiredMargin = marginService.calculateRequiredMargin(order.getProduct(), tradeValue);
            BigDecimal availableMargin = marginService.calculateAvailableMargin(client, tradeDate, order.getProduct(), requiredMargin);

            if (availableMargin.compareTo(requiredMargin) < 0) {
                result.addFailure(BR_VAL_AVAILABLE_FUNDS,
                        String.format("Insufficient funds/margin for intraday short sell. Required Margin: %s, Available Margin: %s",
                                requiredMargin, availableMargin));
            }
        }

        // PRECEDENCE STEP 3: Price-band check (+/- 10% of previous day close)
        if (order.getPrice() != null && order.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            Optional<BigDecimal> prevCloseOpt = stockPriceRepository.getPreviousClosePrice(tradeDate, order.getSymbol());
            if (prevCloseOpt.isPresent()) {
                BigDecimal prevClose = prevCloseOpt.get();
                BigDecimal lowerBand = prevClose.multiply(PRICE_BAND_LOWER_FACTOR).setScale(2, RoundingMode.HALF_UP);
                BigDecimal upperBand = prevClose.multiply(PRICE_BAND_UPPER_FACTOR).setScale(2, RoundingMode.HALF_UP);

                if (order.getPrice().compareTo(lowerBand) < 0 || order.getPrice().compareTo(upperBand) > 0) {
                    result.addFailure(BR_VAL_PRICE_BAND,
                            String.format("Order price %s is outside the +/- 10%% operating band [%s, %s] (Previous Close: %s)",
                                    order.getPrice(), lowerBand, upperBand, prevClose));
                }
            }
        }

        return result;
    }

    /**
     * Validates pledge request:
     * - Client exists
     * - Scrip exists
     * - Scrip is pledgeable (GROUP_A or GROUP_B; GROUP_C rejected under BR_VAL_008)
     * - Client has enough unpledged / free shares
     * - Not on a market holiday (BR_VAL_010 / EC-07)
     */
    public ValidationResult validatePledge(String clientId, String symbol, int quantity, LocalDate date) {
        ValidationResult result = new ValidationResult();

        if (date != null && holidayRepository != null && holidayRepository.isNonTradingDay(date)) {
            result.addFailure(BR_VAL_MARKET_HOLIDAY, "Pledge request rejected on market holiday/weekend: " + date);
        }

        Optional<Client> clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            result.addFailure(BR_VAL_CLIENT_EXISTS, "Client does not exist: " + clientId);
        }

        Optional<Scrip> scripOpt = scripRepository.findBySymbol(symbol);
        if (scripOpt.isEmpty()) {
            result.addFailure(BR_VAL_SYMBOL_EXISTS, "Scrip does not exist: " + symbol);
        } else {
            Scrip scrip = scripOpt.get();
            if (!scrip.getPledgeGroup().isPledgeable()) {
                result.addFailure(BR_VAL_PLEDGE_ELIGIBILITY,
                        "Scrip " + symbol + " is in " + scrip.getPledgeGroup() + " and cannot be pledged as collateral.");
            }
        }

        if (quantity <= 0) {
            result.addFailure(BR_VAL_POSITIVE_QTY, "Pledge quantity must be positive: " + quantity);
        } else if (clientOpt.isPresent()) {
            Client client = clientOpt.get();
            int freeShares = settlementService != null
                    ? settlementService.getAvailableFreeHoldingQuantity(client, symbol)
                    : client.getFreeHoldingQuantity(symbol);
            if (freeShares < quantity) {
                result.addFailure(BR_VAL_HOLDINGS_FREE,
                        String.format("Insufficient free shares to pledge. Free: %d, Requested: %d", freeShares, quantity));
            }
        }

        return result;
    }

    public ValidationResult validatePledge(String clientId, String symbol, int quantity) {
        return validatePledge(clientId, symbol, quantity, null);
    }

    /**
     * Validates unpledge request:
     * - Client exists
     * - Quantity <= currently pledged shares (BR_VAL_009)
     */
    public ValidationResult validateUnpledge(String clientId, String symbol, int quantity) {
        ValidationResult result = new ValidationResult();

        Optional<Client> clientOpt = clientRepository.findById(clientId);
        if (clientOpt.isEmpty()) {
            result.addFailure(BR_VAL_CLIENT_EXISTS, "Client does not exist: " + clientId);
            return result;
        }

        if (quantity <= 0) {
            result.addFailure(BR_VAL_POSITIVE_QTY, "Unpledge quantity must be positive: " + quantity);
            return result;
        }

        Client client = clientOpt.get();
        int pledgedShares = client.getPledgedQuantity(symbol);
        if (quantity > pledgedShares) {
            result.addFailure(BR_VAL_UNPLEDGE_QTY,
                    String.format("Cannot unpledge %d shares of %s. Currently pledged: %d", quantity, symbol, pledgedShares));
        }

        return result;
    }
}
