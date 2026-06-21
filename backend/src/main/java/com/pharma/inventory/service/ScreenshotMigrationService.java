package com.pharma.inventory.service;

import com.pharma.inventory.entity.TransactionScreenshot;
import com.pharma.inventory.repository.TransactionScreenshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

/**
 * One-time background migration that compresses existing screenshots stored as
 * uncompressed Base64 in the database.
 *
 * Runs after the application is fully started so it never blocks startup.
 * Processes records in batches of 50. Each screenshot is decoded, compressed
 * via {@link ScreenshotProcessor#compressAndEncode}, re-encoded, and marked
 * {@code compressed = true}. Records already marked {@code compressed = true}
 * are skipped (idempotent).
 *
 * In the unlikely event that any individual compression fails, the exception
 * is logged and the record is skipped; the migration continues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScreenshotMigrationService {

    private static final int BATCH_SIZE = 50;

    private final TransactionScreenshotRepository screenshotRepository;
    private final ScreenshotProcessor screenshotProcessor;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread thread = new Thread(this::runMigration, "screenshot-compression-migration");
        thread.setDaemon(true);
        thread.start();
    }

    private void runMigration() {
        log.info("Screenshot compression migration starting…");
        int processed = 0;
        Page<TransactionScreenshot> page;
        do {
            page = compressBatch(PageRequest.of(0, BATCH_SIZE));
            processed += page.getNumberOfElements();
        } while (page.hasContent());

        if (processed > 0) {
            log.info("Screenshot compression migration complete — {} record(s) compressed.", processed);
        } else {
            log.info("Screenshot compression migration: no uncompressed records found.");
        }
    }

    /**
     * Fetches one page of uncompressed screenshots, compresses each, saves them,
     * and returns the page so the caller can decide whether to continue.
     */
    @Transactional
    public Page<TransactionScreenshot> compressBatch(PageRequest pageRequest) {
        Page<TransactionScreenshot> page = screenshotRepository.findByCompressedIsNull(pageRequest);
        for (TransactionScreenshot ss : page) {
            try {
                byte[] rawBytes = Base64.getDecoder().decode(ss.getData());
                String[] result  = screenshotProcessor.compressAndEncode(rawBytes, ss.getMimeType());
                ss.setData(result[0]);
                ss.setMimeType(result[1]);
                ss.setCompressed(true);
                screenshotRepository.save(ss);
            } catch (Exception e) {
                log.warn("Could not compress screenshot id={}: {}", ss.getId(), e.getMessage());
                // Mark as compressed anyway so we don't retry endlessly
                ss.setCompressed(true);
                screenshotRepository.save(ss);
            }
        }
        return page;
    }
}
