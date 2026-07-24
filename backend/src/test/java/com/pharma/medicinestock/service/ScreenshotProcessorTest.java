package com.pharma.medicinestock.service;

import com.pharma.medicinestock.exception.InvalidScreenshotException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ScreenshotProcessor")
class ScreenshotProcessorTest {

    private ScreenshotProcessor processor;

    @BeforeEach
    void setUp() { processor = new ScreenshotProcessor(); }

    // ── real/fake byte fixtures ─────────────────────────────────────────
    // encodeToBase64 only checks magic bytes (no full decode); compressAndEncode
    // (via encodeAll) fully decodes non-GIF/WebP formats, so those need genuinely
    // valid image bytes, not just a matching signature prefix.

    private static byte[] realPngBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static byte[] realJpegBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    /** Correct magic-byte prefix for the given MIME type, but not a fully valid/decodable file. */
    private static byte[] magicBytesOnly(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 'x', 'x'};
            case "image/jpeg", "image/jpg" -> new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 'x', 'x'};
            case "image/gif" -> "GIF89a-fake-gif-data".getBytes();
            case "image/webp" -> "RIFF????WEBP-fake-data".getBytes();
            default -> throw new IllegalArgumentException("no fixture for " + mimeType);
        };
    }

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
    @DisplayName("accepts all allowed MIME types when magic bytes match")
    void encodeToBase64_allowedMimeType_succeeds(String mimeType) {
        MultipartFile file = new MockMultipartFile("f", "f.png", mimeType, magicBytesOnly(mimeType));
        assertThatNoException().isThrownBy(() -> processor.encodeToBase64(file));
    }

    @Test @DisplayName("returns correct Base64 encoding of file bytes")
    void encodeToBase64_returnsCorrectBase64() throws IOException {
        byte[] content = realPngBytes();
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

    @Test @DisplayName("rejects a spoofed Content-Type whose bytes don't match any real image format")
    void encodeToBase64_spoofedContentType_throwsInvalidScreenshot() {
        MultipartFile file = new MockMultipartFile("f", "f.png", "image/png", "not-actually-an-image".getBytes());
        assertThatThrownBy(() -> processor.encodeToBase64(file))
                .isInstanceOf(InvalidScreenshotException.class)
                .hasMessageContaining("does not match");
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
        byte[] pngMagic = magicBytesOnly("image/png");
        System.arraycopy(pngMagic, 0, exactly5mb, 0, pngMagic.length);
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

    @Test @DisplayName("encodeAll compresses a genuinely valid PNG to JPEG")
    void encodeAll_oneFile_encodedCorrectly() throws IOException {
        MultipartFile file = new MockMultipartFile("f", "a.png", "image/png", realPngBytes());

        List<String[]> result = processor.encodeAll(List.of(file));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)[1]).isEqualTo("image/jpeg");
    }

    @Test @DisplayName("encodeAll compresses multiple valid files in order")
    void encodeAll_twoFiles_bothEncodedInOrder() throws IOException {
        MultipartFile f1 = new MockMultipartFile("f1", "a.png", "image/png", realPngBytes());
        MultipartFile f2 = new MockMultipartFile("f2", "b.jpg", "image/jpeg", realJpegBytes());

        List<String[]> result = processor.encodeAll(List.of(f1, f2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)[1]).isEqualTo("image/jpeg");
        assertThat(result.get(1)[1]).isEqualTo("image/jpeg");
    }

    @Test @DisplayName("encodeAll stores GIF as-is without decoding, preserving its MIME type")
    void encodeAll_gif_storedAsIs() throws IOException {
        byte[] content = magicBytesOnly("image/gif");
        MultipartFile file = new MockMultipartFile("f", "a.gif", "image/gif", content);

        List<String[]> result = processor.encodeAll(List.of(file));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)[0]).isEqualTo(Base64.getEncoder().encodeToString(content));
        assertThat(result.get(0)[1]).isEqualTo("image/gif");
    }

    @Test @DisplayName("encodeAll throws InvalidScreenshotException for invalid MIME in list")
    void encodeAll_invalidMimeInList_throwsInvalidScreenshot() {
        MultipartFile bad = new MockMultipartFile("f", "bad.pdf", "application/pdf", "data".getBytes());
        assertThatThrownBy(() -> processor.encodeAll(List.of(bad)))
                .isInstanceOf(InvalidScreenshotException.class);
    }

    @Test @DisplayName("encodeAll throws for a spoofed Content-Type whose bytes aren't a real image")
    void encodeAll_spoofedContentType_throwsInvalidScreenshot() {
        MultipartFile spoofed = new MockMultipartFile("f", "a.png", "image/png", "totally-not-a-png".getBytes());
        assertThatThrownBy(() -> processor.encodeAll(List.of(spoofed)))
                .isInstanceOf(InvalidScreenshotException.class)
                .hasMessageContaining("does not match");
    }

    @Test @DisplayName("encodeAll throws when magic bytes match but the file isn't actually decodable")
    void encodeAll_validSignatureButUndecodable_throwsInvalidScreenshot() {
        MultipartFile file = new MockMultipartFile("f", "a.png", "image/png", magicBytesOnly("image/png"));
        assertThatThrownBy(() -> processor.encodeAll(List.of(file)))
                .isInstanceOf(InvalidScreenshotException.class);
    }

    // ── compressAndEncode — decompression-bomb guard ────────────────────

    @Test @DisplayName("compressAndEncode's dimension guard does not false-positive on a normal small image")
    void compressAndEncode_normalDimensions_notRejectedByGuard() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", out);
        byte[] tinyValidPng = out.toByteArray();
        String[] result = processor.compressAndEncode(tinyValidPng, "image/png");
        assertThat(result[1]).isEqualTo("image/jpeg");
    }
}
