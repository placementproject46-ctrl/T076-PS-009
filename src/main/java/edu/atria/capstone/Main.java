package edu.atria.capstone;

import edu.atria.capstone.engine.BrokerageEngine;
import edu.atria.capstone.engine.HappyPathDemonstrator;
import edu.atria.capstone.model.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        try {
            BrokerageEngine engine = new BrokerageEngine();
            loadInitialData(engine);

            if (args.length == 0 || args[0].equalsIgnoreCase("--demo")) {
                HappyPathDemonstrator demonstrator = new HappyPathDemonstrator(engine);
                demonstrator.runDemonstration();
            } else if (args[0].equalsIgnoreCase("--commands")) {
                String cmdFile = args.length > 1 ? args[1] : "sample-data/T076-PS-009-sample-data/commands.txt";
                runCommandStream(engine, Paths.get(cmdFile));
            } else if (args[0].equalsIgnoreCase("--interactive") || args[0].equalsIgnoreCase("-i")) {
                runInteractiveConsole(engine);
            } else {
                printHelp();
            }
        } catch (Exception e) {
            System.err.println("Fatal execution error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void loadInitialData(BrokerageEngine engine) {
        // Try loading from workspace disk path first, fallback to classpath resources
        Path sampleDataDir = Paths.get("sample-data", "T076-PS-009-sample-data");
        if (Files.exists(sampleDataDir)) {
            try {
                engine.getDataLoader().loadAll(sampleDataDir);
                return;
            } catch (Exception ignored) {}
        }

        // Fallback to classpath resources
        try {
            loadClasspathResource(engine, "/sample-data/clients.csv", (e, in) -> e.getDataLoader().loadClients(in));
            loadClasspathResource(engine, "/sample-data/scrips.csv", (e, in) -> e.getDataLoader().loadScrips(in));
            loadClasspathResource(engine, "/sample-data/holdings_opening.csv", (e, in) -> e.getDataLoader().loadOpeningHoldings(in));
            loadClasspathResource(engine, "/sample-data/prices.csv", (e, in) -> e.getDataLoader().loadPrices(in));
            loadClasspathResource(engine, "/sample-data/holidays.csv", (e, in) -> e.getDataLoader().loadHolidays(in));
        } catch (Exception e) {
            System.err.println("Notice: Could not load all initial files from classpath: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ResourceConsumer {
        void accept(BrokerageEngine engine, InputStream in) throws Exception;
    }

    private static void loadClasspathResource(BrokerageEngine engine, String path, ResourceConsumer consumer) {
        try (InputStream in = Main.class.getResourceAsStream(path)) {
            if (in != null) {
                consumer.accept(engine, in);
            }
        } catch (Exception e) {
            System.err.println("Failed to load resource " + path + ": " + e.getMessage());
        }
    }

    private static void runCommandStream(BrokerageEngine engine, Path commandFile) {
        System.out.println("Processing command file: " + commandFile.toAbsolutePath());
        try {
            if (!Files.exists(commandFile)) {
                // Try classpath
                try (InputStream in = Main.class.getResourceAsStream("/sample-data/commands.txt")) {
                    if (in != null) {
                        engine.executeCommandFile(in);
                    } else {
                        System.err.println("Command file not found at " + commandFile);
                        return;
                    }
                }
            } else {
                engine.executeCommandFile(commandFile);
            }

            Path outputDir = Paths.get("output");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            engine.getReportService().writeOrderDecisionsCsv(outputDir.resolve("order_decisions.csv"));
            engine.getReportService().writeContractNotesCsv(outputDir.resolve("contract_notes.csv"));
            engine.getReportService().writeHoldingsReportCsv(outputDir.resolve("holdings_report.csv"), engine.getCurrentDate());

            System.out.println("All commands processed successfully.");
            System.out.println("Generated evaluation CSV reports in " + outputDir.toAbsolutePath());
            System.out.println("  1. " + outputDir.resolve("order_decisions.csv"));
            System.out.println("  2. " + outputDir.resolve("contract_notes.csv"));
            System.out.println("  3. " + outputDir.resolve("holdings_report.csv"));

        } catch (Exception e) {
            System.err.println("Error executing command stream: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runInteractiveConsole(BrokerageEngine engine) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("==================================================================");
        System.out.println(" STOCK BROKERAGE ENGINE - INTERACTIVE CONSOLE (TEAM T076 / PS-009)");
        System.out.println(" Type 'help' for command list or 'exit' to quit.");
        System.out.println("==================================================================");

        while (true) {
            System.out.print("\nengine> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) break;
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("help")) {
                printHelp();
            } else if (line.equalsIgnoreCase("clients")) {
                for (Client c : engine.getClientRepository().findAll()) {
                    System.out.printf("[%s] %-18s | Cash: Rs %10s | PAN: %s\n",
                            c.getClientId(), c.getClientName(), c.getCashBalance(), c.getPan());
                }
            } else if (line.startsWith("report ")) {
                String clientId = line.substring(7).trim();
                System.out.println(engine.getReportService().generateClientAccountReport(clientId, engine.getCurrentDate()));
            } else {
                engine.executeCommand(line);
            }
        }
    }

    private static void printHelp() {
        System.out.println("Stock Brokerage Engine CLI Usage:");
        System.out.println("  java -jar stock-brokerage-engine-1.0.0.jar --demo               Run Week 2 Happy-Path Demonstration");
        System.out.println("  java -jar stock-brokerage-engine-1.0.0.jar --commands <file>    Execute batch command stream file");
        System.out.println("  java -jar stock-brokerage-engine-1.0.0.jar --interactive        Launch interactive terminal console");
        System.out.println("\nInteractive / Stream Commands:");
        System.out.println("  ORDER|<id>|<timestamp>|<client_id>|<symbol>|<side>|<product>|<qty>|<price>");
        System.out.println("  PLEDGE|<id>|<timestamp>|<client_id>|<symbol>|<qty>");
        System.out.println("  UNPLEDGE|<id>|<timestamp>|<client_id>|<symbol>|<qty>");
        System.out.println("  FUNDS_IN|<id>|<timestamp>|<client_id>|<amount>");
        System.out.println("  report <client_id>                                              Print client account summary");
        System.out.println("  clients                                                         List registered clients");
    }
}
