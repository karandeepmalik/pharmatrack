package com.pharma.inventory.repository;

import com.pharma.inventory.entity.TransactionScreenshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionScreenshotRepository extends JpaRepository<TransactionScreenshot, Long> {

    /** Used by the compression migration to find unprocessed screenshots. */
    Page<TransactionScreenshot> findByCompressedIsNull(Pageable pageable);
}
