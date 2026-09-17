package com.notenest.noteservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// Menyalakan pengisian otomatis createdAt/updatedAt milik BaseEntity
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
