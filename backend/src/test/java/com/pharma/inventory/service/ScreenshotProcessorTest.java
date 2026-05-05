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
import java.util.List;

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

    // ── encodeAll ─────────────────────────────────────────────────────

    @Test @DisplayName("encodeAll returns empty list for null input")
    void encodeAll_null_returnsEmpty() throws IOException {
        assertThat(processor.encodeAll(null)).isEmpty();
    }

    @Test @DisplayName("encodeAll returns empty list for empty list")
    void encodeAll_emptyList_returnsEmpty() throws IOException {
        assertThat(processor.encodeAll(List.of())).isEmpty();
    }

    @Test @DisplayName("encodeAll skips null and empty files")
    void encodeAll_nullAndEmptyEntries_skipped() throws IOException {
        MultipartFile empty = new MockMultipartFile("f", new byte[0]);
        List<String[]> result = processor.encodeAll(List.of(empty));
        assertThat(result).isEmpty();
    }

    @Test @DisplayName("encodeAll encodes single valid file")
    void encodeAll_oneFile_encodedCorrectly() throws IOException {
        byte[] content = "png-data".getBytes();
        MultipartFile file = new MockMultipartFile("f", "a.png", "image/png", content);

        List<String[]> result = processor.encodeAll(List.of(file));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)[0]).isEqualTo(Base64.getEncoder().encodeToString(content));
        assertThat(result.get(0)[1]).isEqualTo("image/png");
    }

    @Test @DisplayName("encodeAll encodes multiple valid files in order")
    void encodeAll_twoFiles_bothEncodedInOrder() throws IOException {
        byte[] content1 = "png1".getBytes();
        byte[] content2 = "jpg2".getBytes();
        MultipartFile f1 = new MockMultipartFile("f1", "a.png", "image/png", content1);
        MultipartFile f2 = new MockMultipartFile("f2", "b.jpg", "image/jpeg", content2);

        List<String[]> result = processor.encodeAll(List.of(f1, f2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)[1]).isEqualTo("image/png");
        assertThat(result.get(1)[1]).isEqualTo("image/jpeg");
    }

    @Test @DisplayName("encodeAll throws InvalidScreenshotException for invalid MIME in list")
    void encodeAll_invalidMimeInList_throwsInvalidScreenshot() {
        MultipartFile bad = new MockMultipartFile("f", "bad.pdf", "application/pdf", "data".getBytes());
        assertThatThrownBy(() -> processor.encodeAll(List.of(bad)))
                .isInstanceOf(InvalidScreenshotException.class);
    }
}
