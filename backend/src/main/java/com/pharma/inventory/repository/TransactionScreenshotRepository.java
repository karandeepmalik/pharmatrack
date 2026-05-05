package com.pharma.inventory.repository;

import com.pharma.inventory.entity.TransactionScreenshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionScreenshotRepository extends JpaRepository<TransactionScreenshot, Long> {
}
