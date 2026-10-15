package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<Certificate> findByCertificateCode(String certificateCode);
}