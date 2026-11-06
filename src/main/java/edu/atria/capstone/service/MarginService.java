package edu.atria.capstone.service;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.ScripRepository;
import edu.atria.capstone.repository.StockPriceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public class MarginService {
    private static final BigDecimal DELIVERY_MARGIN_RATE = new BigDecimal("1.00"); // 100%
    private static final BigDecimal INTRADAY_MARGIN_RATE = new BigDecimal("0.20"); // 20%
    private static final BigDecimal COLLATERAL_MARGIN_CAP_RATE = new BigDecimal("0.50"); // 50% cap

    private final ScripRepository scripRepository;
    private final StockPriceRepository stockPriceRepository;

    public MarginService(ScripRepository scripRepository, StockPriceRepository stockPriceRepository) {
        this.scripRepository = scripRepository;
        this.stockPriceRepository = stockPriceRepository;
    }

    /**
     * Trade Value = Quantity * Price
     */
    public BigDecimal calculateTradeValue(int quantity, BigDecimal price) {
        if (price == null || quantity <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Margin requirement:
     * - Delivery: 100% of trade value
     * - Intraday: 20% of trade value
     */
    public BigDecimal calculateRequiredMargin(ProductType product, BigDecimal tradeValue) {
        if (tradeValue == null || tradeValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (product == ProductType.DELIVERY) {
            return tradeValue.multiply(DELIVERY_MARGIN_RATE).setScale(2, RoundingMode.HALF_UP);
        } else {
            return tradeValue.multiply(INTRADAY_MARGIN_RATE).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Calculates total collateral value of client's pledged shares on a given date.
     * Collateral Value = sum(Pledged Quantity * Close Price * (1 - Haircut))
     */
    public BigDecimal calculateCollateralValue(Client client, LocalDate date) {
        if (client == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalCollateral = BigDecimal.ZERO;
        for (Map.Entry<String, Holding> entry : client.getHoldings().entrySet()) {
            Holding holding = entry.getValue();
            if (holding.getPledgedQty() > 0) {
                String symbol = entry.getKey();
                Optional<Scrip> scripOpt = scripRepository.findBySymbol(symbol);
                PledgeGroup group = scripOpt.map(Scrip::getPledgeGroup).orElse(PledgeGroup.GROUP_C);

                if (group.isPledgeable()) {
                    Optional<BigDecimal> priceOpt = stockPriceRepository.getCurrentOrLatestPrice(date, symbol);
                    if (priceOpt.isPresent()) {
                        BigDecimal price = priceOpt.get();
                        BigDecimal marketValue = price.multiply(BigDecimal.valueOf(holding.getPledgedQty()));
                        BigDecimal collateralForHolding = marketValue.multiply(group.getCollateralFactor());
                        totalCollateral = totalCollateral.add(collateralForHolding);
                    }
                }
            }
        }
        return totalCollateral.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates available margin for an order:
     * - For DELIVERY: Free ledger cash balance.
     * - For INTRADAY: Cash balance + Collateral value (collateral contribution capped at 50% of required margin).
     */
    public BigDecimal calculateAvailableMargin(Client client, LocalDate date, ProductType product, BigDecimal requiredMargin) {
        if (client == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal cash = client.getCashBalance();
        if (product == ProductType.DELIVERY) {
            return cash.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        // INTRADAY: Cash + Collateral (50:50 rule)
        BigDecimal totalCollateral = calculateCollateralValue(client, date);
        if (requiredMargin == null || requiredMargin.compareTo(BigDecimal.ZERO) <= 0) {
            return cash.add(totalCollateral).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        // Collateral can cover at most 50% of required margin
        BigDecimal maxCollateralUsable = requiredMargin.multiply(COLLATERAL_MARGIN_CAP_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal effectiveCollateral = totalCollateral.min(maxCollateralUsable);

        // Usable margin for this order = Cash + Effective Collateral
        return cash.add(effectiveCollateral).setScale(2, RoundingMode.HALF_UP);
    }
}
