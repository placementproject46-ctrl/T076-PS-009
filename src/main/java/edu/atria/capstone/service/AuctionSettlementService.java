package edu.atria.capstone.service;

import edu.atria.capstone.model.AuctionRecord;
import edu.atria.capstone.model.Client;
import edu.atria.capstone.model.LedgerEntry;
import edu.atria.capstone.repository.ClientRepository;
import edu.atria.capstone.repository.LedgerRepository;
import edu.atria.capstone.repository.StockPriceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class AuctionSettlementService {
    private final ClientRepository clientRepository;
    private final StockPriceRepository stockPriceRepository;
    private final LedgerRepository ledgerRepository;

    private final List<AuctionRecord> auctionRecords = new ArrayList<>();
    private final AtomicLong auctionSequence = new AtomicLong(1);

    public AuctionSettlementService(ClientRepository clientRepository,
                                  StockPriceRepository stockPriceRepository,
                                  LedgerRepository ledgerRepository) {
        this.clientRepository = clientRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * Executes short delivery auction:
     * Settled at closing price + 20% penalty on shortfall value.
     */
    public AuctionRecord settleShortDelivery(String clientId, String symbol, int shortfallQuantity, LocalDate settlementDate) {
        if (shortfallQuantity <= 0) return null;

        BigDecimal closePrice = stockPriceRepository.getClosePrice(settlementDate, symbol)
                .orElse(stockPriceRepository.getCurrentOrLatestPrice(settlementDate, symbol).orElse(BigDecimal.ZERO));

        String auctionId = String.format("AUC-%05d", auctionSequence.getAndIncrement());
        AuctionRecord record = new AuctionRecord(auctionId, settlementDate, clientId, symbol, shortfallQuantity, closePrice);
        auctionRecords.add(record);

        Client client = clientRepository.findById(clientId).orElse(null);
        if (client != null) {
            // Debit defaulting seller for shortfall value + 20% penalty
            client.debitCash(record.getTotalDebit());

            ledgerRepository.save(new LedgerEntry(
                    ledgerRepository.nextEntryId(),
                    LocalDateTime.of(settlementDate, java.time.LocalTime.of(15, 30, 0)),
                    settlementDate,
                    clientId,
                    "SHORT_DELIVERY_AUCTION_DEBIT",
                    record.getTotalDebit(),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    client.getCashBalance(),
                    String.format("Auction penalty for %d short shares of %s @ %s (+20%% penalty: Rs %s)",
                            shortfallQuantity, symbol, closePrice, record.getPenaltyAmount()),
                    auctionId
            ));
        }

        return record;
    }

    public List<AuctionRecord> getAuctionRecords() {
        return Collections.unmodifiableList(auctionRecords);
    }

    public void clear() {
        auctionRecords.clear();
        auctionSequence.set(1);
    }
}
