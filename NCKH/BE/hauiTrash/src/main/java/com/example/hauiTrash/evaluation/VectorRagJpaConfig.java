package com.example.hauiTrash.evaluation;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Profile("vector-rag-test")
public class VectorRagJpaConfig {

    @Bean(name = {"transactionManager", "vectorRagJpaTransactionManager"})
    @Primary
    public PlatformTransactionManager vectorRagJpaTransactionManager(
            EntityManagerFactory entityManagerFactory) {

        return new JpaTransactionManager(entityManagerFactory);
    }
}
