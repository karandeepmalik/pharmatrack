package com.pharma.inventory.service;

import com.pharma.inventory.exception.InvalidScreenshotException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ScreenshotProcessor")
class ScreenshotProcessorTest {

    private ScreenshotProcessor processor;

    @BeforeEach
    void setUp() { processor = new ScreenshotProcessor(); }

    // ── hasScreenshot ──────────────────────────────────────────────────

    @Test @DisplayName("hasScreenshot returns false for null")
    void hasScreenshot_null_returnsFalse() {
        assertThat(processor.hasScreenshot(null)).isFalse();
    }

    @Test @DisplayName("hasScreenshot returns false for empty file")
    void hasScreenshot_emptyFile_returnsFalse() {
        MultipartFile empty = new MockMultipartFile("file", new byte[0]);
        assertThat(processor.hasScreenshot(empty)).isFalse();
    }

    @Test @DisplayName("hasScreenshot returns true for non-empty file")
    void hasScreenshot_nonEmpty_returnsTrue() {
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "data".getBytes());
        assertThat(processor.hasScreenshot(file)).isTrue();
    }

    // ── encodeToBase64 — valid types ────────────────────────────────────

    @ParameterizedTest(name = "accepts {0}")
    @ValueSource(strings = {"image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif"})
    @DisplayName("accepts all allowed MIME types")
    void encodeToBase64_allowedMimeType_succeeds(String mimeType) {
        MultipartFile file = new MockMultipartFile("f", "f.png", mimeType, "bytes".getBytes());
        assertThatNoException().isThrownBy(() -> processor.encodeToBase64(file));
    }

    @Test @DisplayName("returns correct Base64 encoding of file bytes")
    void encodeToBase64_returnsCorrectBase64() throws IOException {
        byte[] content = "png-content".getBytes();
        MultipartFile file = new MockMultipartFile("f", "f.png", "image/png", content);

        String result = processor.encodeToBase64(file);
        assertThat(result).isEqualTo(Base64.getEncoder().encodeToString(content));
    }

    // ── encodeToBase64 — invalid type ───────────────────────────────────

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(strings = {"application/pdf", "text/plain", "application/octet-stream", "video/mp4"})
    @DisplayName("rejects non-image MIME types")
    void encodeToBase64_nonImageMime_throwsInvalidScreenshot(String mimeType) {
        MultipartFile file = new MockMultipartFile("f", "f.pdf", mimeType, "data".getBytes());
        assertThatThrownBy(() -> processor.encodeToBase64(file))
                .isInstanceOf(InvalidScreenshotException.class)
                .hasMessageContaining(mimeType);
    }

    @Test @DisplayName("rejects null MIME type")
    void encodeToBase64_nullMime_throwsInvalidScreenshot() {
        MultipartFile file = new MockMultipartFile("f", "f.bin", null, "data".getBytes());
        assertThatThrownBy(() -> processor.encodeToBase64(file))
                .isInstanceOf(InvalidScreenshotException.class);
    }

    // ── encodeToBase64 — size limit ─────────────────────────────────────

    @Test @DisplayName("rejects file exceeding 5 MB")
    void encodeToBase64_oversized_throwsInvalidScreenshot() {
        byte[] large = new byte[5 * 1024 * 1024 + 1];
        MultipartFile file = new MockMultipartFile("f", "big.png", "image/png", large);
        assertThatThrownBy(() -> processor.encodeToBase64(file))
                .isInstanceOf(InvalidScreenshotException.class)
                .hasMessageContaining("5 MB");
    }

    @Test @DisplayName("accepts file exactly at 5 MB limit")
    void encodeToBase64_exactlyAtLimit_succeeds() {
        byte[] exactly5mb = new byte[5 * 1024 * 1024];
        MultipartFile file = new MockMultipartFile("f", "ok.png", "image/png", exactly5mb);
        assertThatNoException().isThrownBy(() -> processor.encodeToBase64(file));
    }
}
