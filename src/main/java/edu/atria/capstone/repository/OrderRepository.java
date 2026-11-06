package edu.atria.capstone.repository;

import edu.atria.capstone.model.ContractNote;
import edu.atria.capstone.model.Order;
import edu.atria.capstone.model.OrderDecisionRecord;

import java.util.*;

public class OrderRepository {
    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final List<OrderDecisionRecord> decisionRecords = new ArrayList<>();
    private final List<ContractNote> contractNotes = new ArrayList<>();

    public void save(Order order) {
        if (order != null) {
            orders.put(order.getOrderId(), order);
        }
    }

    public Optional<Order> findById(String orderId) {
        if (orderId == null) return Optional.empty();
        return Optional.ofNullable(orders.get(orderId));
    }

    public void addDecisionRecord(OrderDecisionRecord record) {
        if (record != null) {
            decisionRecords.add(record);
        }
    }

    public void addContractNote(ContractNote note) {
        if (note != null) {
            contractNotes.add(note);
        }
    }

    public List<Order> findAllOrders() {
        return new ArrayList<>(orders.values());
    }

    public List<OrderDecisionRecord> getDecisionRecords() {
        return Collections.unmodifiableList(decisionRecords);
    }

    public List<ContractNote> getContractNotes() {
        return Collections.unmodifiableList(contractNotes);
    }

    public void clear() {
        orders.clear();
        decisionRecords.clear();
        contractNotes.clear();
    }
}
