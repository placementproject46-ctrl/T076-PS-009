package edu.atria.capstone.repository;

import edu.atria.capstone.model.PledgeGroup;
import edu.atria.capstone.model.Scrip;

import java.util.*;

public class ScripRepository {
    private final Map<String, Scrip> scrips = new LinkedHashMap<>();

    public void save(Scrip scrip) {
        if (scrip != null) {
            scrips.put(scrip.getSymbol().toUpperCase(), scrip);
        }
    }

    public Optional<Scrip> findBySymbol(String symbol) {
        if (symbol == null) return Optional.empty();
        return Optional.ofNullable(scrips.get(symbol.trim().toUpperCase()));
    }

    public boolean existsBySymbol(String symbol) {
        if (symbol == null) return false;
        return scrips.containsKey(symbol.trim().toUpperCase());
    }

    public boolean retagGroup(String symbol, PledgeGroup newGroup) {
        Optional<Scrip> opt = findBySymbol(symbol);
        if (opt.isPresent()) {
            opt.get().setPledgeGroup(newGroup);
            return true;
        }
        return false;
    }

    public List<Scrip> findAll() {
        return new ArrayList<>(scrips.values());
    }

    public int count() {
        return scrips.size();
    }

    public void clear() {
        scrips.clear();
    }
}
