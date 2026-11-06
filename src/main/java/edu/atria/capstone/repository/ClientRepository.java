package edu.atria.capstone.repository;

import edu.atria.capstone.model.Client;

import java.util.*;

public class ClientRepository {
    private final Map<String, Client> clients = new LinkedHashMap<>();

    public void save(Client client) {
        if (client != null) {
            clients.put(client.getClientId(), client);
        }
    }

    public Optional<Client> findById(String clientId) {
        if (clientId == null) return Optional.empty();
        return Optional.ofNullable(clients.get(clientId.trim()));
    }

    public boolean existsById(String clientId) {
        if (clientId == null) return false;
        return clients.containsKey(clientId.trim());
    }

    public List<Client> findAll() {
        return new ArrayList<>(clients.values());
    }

    public int count() {
        return clients.size();
    }

    public void clear() {
        clients.clear();
    }
}
