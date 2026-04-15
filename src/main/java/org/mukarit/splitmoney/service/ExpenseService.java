package org.mukarit.splitmoney.service;

import lombok.RequiredArgsConstructor;
import org.mukarit.splitmoney.entity.Expense;
import org.mukarit.splitmoney.entity.ExpenseParticipant;
import org.mukarit.splitmoney.entity.User;
import org.mukarit.splitmoney.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {
    private final ExpenseRepository expenseRepository;

    @Transactional
    public Expense addExpense(User payer, BigDecimal amount, String description, Long groupId, List<Long> participantIds) {
        Expense expense = Expense.builder()
                .payer(payer)
                .amount(amount)
                .description(description)
                .groupId(groupId)
                .createdAt(LocalDateTime.now())
                .build();

        BigDecimal share = amount.divide(BigDecimal.valueOf(participantIds.size()), 2, RoundingMode.HALF_UP);
        List<ExpenseParticipant> participants = participantIds.stream()
                .map(userId -> ExpenseParticipant.builder()
                        .expense(expense)
                        .userId(userId)
                        .shareAmount(share)
                        .build())
                .collect(Collectors.toList());

        expense.setParticipants(participants);
        return expenseRepository.save(expense);
    }

    public List<Expense> getGroupHistory(Long groupId) {
        return expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }
    
    public List<Expense> getAllExpenses() {
        return expenseRepository.findAll();
    }

    @Transactional
    public void deleteExpense(Long expenseId) {
        expenseRepository.deleteById(expenseId);
    }

    public Map<Long, Map<Long, BigDecimal>> calculateBalances(Long groupId, List<User> members) {
        List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        Map<Long, BigDecimal> netBalances = new HashMap<>();
        
        for (User member : members) {
            netBalances.put(member.getId(), BigDecimal.ZERO);
        }

        for (Expense expense : expenses) {
            Long payerId = expense.getPayer().getId();
            netBalances.put(payerId, netBalances.get(payerId).add(expense.getAmount()));
            
            for (ExpenseParticipant participant : expense.getParticipants()) {
                Long pId = participant.getUserId();
                netBalances.put(pId, netBalances.get(pId).subtract(participant.getShareAmount()));
            }
        }

        return optimizeDebts(netBalances);
    }

    private Map<Long, Map<Long, BigDecimal>> optimizeDebts(Map<Long, BigDecimal> balances) {
        // Simple debt optimization: minimize transactions
        List<Map.Entry<Long, BigDecimal>> debtors = balances.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) < 0)
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toList());

        List<Map.Entry<Long, BigDecimal>> creditors = balances.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .collect(Collectors.toList());

        Map<Long, Map<Long, BigDecimal>> debts = new HashMap<>();

        int dIdx = 0, cIdx = 0;
        while (dIdx < debtors.size() && cIdx < creditors.size()) {
            Map.Entry<Long, BigDecimal> debtor = debtors.get(dIdx);
            Map.Entry<Long, BigDecimal> creditor = creditors.get(cIdx);

            BigDecimal amountToPay = debtor.getValue().abs().min(creditor.getValue());
            
            debts.computeIfAbsent(debtor.getKey(), k -> new HashMap<>())
                 .put(creditor.getKey(), amountToPay);

            debtor.setValue(debtor.getValue().add(amountToPay));
            creditor.setValue(creditor.getValue().subtract(amountToPay));

            if (debtor.getValue().compareTo(BigDecimal.ZERO) == 0) dIdx++;
            if (creditor.getValue().compareTo(BigDecimal.ZERO) == 0) cIdx++;
        }

        return debts;
    }

    @Transactional
    public Expense addPayment(User payer, User receiver, BigDecimal amount, Long groupId) {
        Expense expense = Expense.builder()
                .payer(payer)
                .amount(amount)
                .description("Payment to " + receiver.getName())
                .groupId(groupId)
                .createdAt(LocalDateTime.now())
                .build();

        ExpenseParticipant participant = ExpenseParticipant.builder()
                .expense(expense)
                .userId(receiver.getId())
                .shareAmount(amount)
                .build();

        expense.setParticipants(List.of(participant));
        return expenseRepository.save(expense);
    }
}
