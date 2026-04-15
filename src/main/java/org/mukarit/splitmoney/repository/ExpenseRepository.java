package org.mukarit.splitmoney.repository;

import org.mukarit.splitmoney.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByGroupIdOrderByCreatedAtDesc(Long groupId);
}
