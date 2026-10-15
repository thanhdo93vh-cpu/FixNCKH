package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface  AccountRepository extends JpaRepository<Account, Integer> {
    Optional<Account> findByPhone(String phone);
    Optional<Account> findByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);
}
