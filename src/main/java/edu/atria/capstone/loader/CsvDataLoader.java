package edu.atria.capstone.loader;

import edu.atria.capstone.model.*;
import edu.atria.capstone.repository.*;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

public class CsvDataLoader {

    private final ClientRepository clientRepository;
    private final ScripRepository scripRepository;
    private final StockPriceRepository stockPriceRepository;
    private final HolidayRepository holidayRepository;

    public CsvDataLoader(ClientRepository clientRepository,
                         ScripRepository scripRepository,
                         StockPriceRepository stockPriceRepository,
                         HolidayRepository holidayRepository) {
        this.clientRepository = clientRepository;
        this.scripRepository = scripRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.holidayRepository = holidayRepository;
    }

    public void loadClients(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split(",", -1);
                if (tokens.length >= 6) {
                    String clientId = tokens[0].trim();
                    String clientName = tokens[1].trim();
                    String pan = tokens[2].trim();
                    String mobile = tokens[3].trim();
                    BigDecimal openingCash = new BigDecimal(tokens[4].trim()).setScale(2, RoundingMode.HALF_UP);
                    LocalDate openingDate = LocalDate.parse(tokens[5].trim());

                    Client client = new Client(clientId, clientName, pan, mobile, openingCash, openingDate);
                    clientRepository.save(client);
                }
            }
        }
    }

    public void loadClients(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            loadClients(in);
        }
    }

    public void loadScrips(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split(",", -1);
                if (tokens.length >= 4) {
                    String symbol = tokens[0].trim().toUpperCase();
                    String name = tokens[1].trim();
                    PledgeGroup group = PledgeGroup.fromString(tokens[2].trim());
                    int lotSize = Integer.parseInt(tokens[3].trim());

                    Scrip scrip = new Scrip(symbol, name, group, lotSize);
                    scripRepository.save(scrip);
                }
            }
        }
    }

    public void loadScrips(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            loadScrips(in);
        }
    }

    public void loadOpeningHoldings(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split(",", -1);
                if (tokens.length >= 5) {
                    String clientId = tokens[0].trim();
                    String symbol = tokens[1].trim().toUpperCase();
                    int qty = Integer.parseInt(tokens[2].trim());
                    int pledgedQty = Integer.parseInt(tokens[3].trim());
                    BigDecimal avgCost = new BigDecimal(tokens[4].trim()).setScale(2, RoundingMode.HALF_UP);

                    Optional<Client> clientOpt = clientRepository.findById(clientId);
                    clientOpt.ifPresent(c -> c.addOrUpdateHolding(symbol, qty, pledgedQty, avgCost));
                }
            }
        }
    }

    public void loadOpeningHoldings(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            loadOpeningHoldings(in);
        }
    }

    public void loadPrices(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split(",", -1);
                if (tokens.length >= 3) {
                    LocalDate date = LocalDate.parse(tokens[0].trim());
                    String symbol = tokens[1].trim().toUpperCase();
                    BigDecimal closePrice = new BigDecimal(tokens[2].trim()).setScale(2, RoundingMode.HALF_UP);

                    stockPriceRepository.save(date, symbol, closePrice);
                }
            }
        }
    }

    public void loadPrices(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            loadPrices(in);
        }
    }

    public void loadHolidays(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split(",", -1);
                if (tokens.length >= 2) {
                    LocalDate date = LocalDate.parse(tokens[0].trim());
                    String name = tokens[1].trim();

                    holidayRepository.save(new Holiday(date, name));
                }
            }
        }
    }

    public void loadHolidays(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            loadHolidays(in);
        }
    }

    /**
     * Loads all data files from a directory.
     */
    public void loadAll(Path dir) throws IOException {
        Path clientsFile = dir.resolve("clients.csv");
        if (Files.exists(clientsFile)) loadClients(clientsFile);

        Path scripsFile = dir.resolve("scrips.csv");
        if (Files.exists(scripsFile)) loadScrips(scripsFile);

        Path holdingsFile = dir.resolve("holdings_opening.csv");
        if (Files.exists(holdingsFile)) loadOpeningHoldings(holdingsFile);

        Path pricesFile = dir.resolve("prices.csv");
        if (Files.exists(pricesFile)) loadPrices(pricesFile);

        Path holidaysFile = dir.resolve("holidays.csv");
        if (Files.exists(holidaysFile)) loadHolidays(holidaysFile);
    }
}
