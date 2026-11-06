package edu.atria.capstone.repository;

import edu.atria.capstone.model.LedgerEntry;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class LedgerRepository {
    private final List<LedgerEntry> allEntries = new ArrayList<>();
    private final Map<String, List<LedgerEntry>> entriesByClient = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    public String nextEntryId() {
        return String.format("LED-%05d", sequence.getAndIncrement());
    }

    public synchronized void save(LedgerEntry entry) {
        if (entry != null) {
            allEntries.add(entry);
            entriesByClient.computeIfAbsent(entry.getClientId(), k -> new ArrayList<>()).add(entry);
        }
    }

    public synchronized List<LedgerEntry> findByClientId(String clientId) {
        if (clientId == null) return Collections.emptyList();
        List<LedgerEntry> list = entriesByClient.get(clientId);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    public synchronized List<LedgerEntry> findByClientIdAndDate(String clientId, LocalDate date) {
        return findByClientId(clientId).stream()
                .filter(e -> date.equals(e.getSettlementDate()) || date.equals(e.getTimestamp().toLocalDate()))
                .collect(Collectors.toList());
    }

    public synchronized List<LedgerEntry> findAll() {
        return new ArrayList<>(allEntries);
    }

    public synchronized void clear() {
        allEntries.clear();
        entriesByClient.clear();
        sequence.set(1);
    }
}
