package com.pharma.inventory.service;

import com.pharma.inventory.exception.InvalidScreenshotException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Validates, compresses, and Base64-encodes uploaded payment screenshots.
 *
 * Images are resized to at most MAX_DIMENSION px on the longest side and
 * re-encoded as JPEG at JPEG_QUALITY before storage. If the image cannot be
 * decoded (e.g. WebP without native support, corrupt data) the original bytes
 * are stored unchanged so the operation never hard-fails.
 *
 * GIFs are stored as-is to preserve any animation.
 */
@Service
public class ScreenshotProcessor {

    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final int  MAX_DIMENSION = 1200;          // px on longest side
    private static final float JPEG_QUALITY = 0.80f;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "image/jpg",
        "image/webp",
        "image/gif"
    );

    /**
     * Validates a screenshot MultipartFile and returns its Base64-encoded content.
     * Does NOT compress — compression happens in {@link #encodeAll} so the MIME
     * type can be updated alongside the data.
     */
    public String encodeToBase64(MultipartFile file) throws IOException {
        validateContentType(file);
        validateSize(file);
        return Base64.getEncoder().encodeToString(file.getBytes());
    }

    public boolean hasScreenshot(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    /**
     * Validates, compresses, and Base64-encodes a list of screenshots.
     * Each element of the returned list is {@code [base64Data, mimeType]}.
     * Files that are null or empty are skipped.
     * Compression converts JPEG/PNG to a resized JPEG; GIF and undecodable
     * formats fall back to the original bytes with the original MIME type.
     */
    public List<String[]> encodeAll(List<MultipartFile> files) throws IOException {
        List<String[]> result = new ArrayList<>();
        if (files == null) return result;
        for (MultipartFile file : files) {
            if (hasScreenshot(file)) {
                validateContentType(file);
                validateSize(file);
                result.add(compressAndEncode(file.getBytes(), file.getContentType()));
            }
        }
        return result;
    }

    /**
     * Compresses raw image bytes and returns {@code [base64Data, mimeType]}.
     * Falls back to original bytes if the image cannot be decoded.
     */
    public String[] compressAndEncode(byte[] rawBytes, String originalMimeType) {
        // Preserve GIFs as-is (may be animated)
        if ("image/gif".equalsIgnoreCase(originalMimeType)) {
            return new String[]{Base64.getEncoder().encodeToString(rawBytes), originalMimeType};
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (img == null) {
                // Format not decodable by standard Java ImageIO (e.g. WebP)
                return new String[]{Base64.getEncoder().encodeToString(rawBytes), originalMimeType};
            }
            img = resizeIfNeeded(img);
            byte[] compressed = encodeAsJpeg(img);
            return new String[]{Base64.getEncoder().encodeToString(compressed), "image/jpeg"};
        } catch (Exception e) {
            return new String[]{Base64.getEncoder().encodeToString(rawBytes), originalMimeType};
        }
    }

    // ── Private helpers ───────────────────────────────────────────────

    private BufferedImage resizeIfNeeded(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= MAX_DIMENSION && h <= MAX_DIMENSION) return img;
        double scale = (double) MAX_DIMENSION / Math.max(w, h);
        int nw = Math.max(1, (int) (w * scale));
        int nh = Math.max(1, (int) (h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, nw, nh, Color.WHITE, null);
        g.dispose();
        return out;
    }

    private byte[] encodeAsJpeg(BufferedImage img) throws IOException {
        // Flatten to RGB (JPEG has no alpha channel)
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(img, 0, 0, Color.WHITE, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgb, null, null), param);
        }
        writer.dispose();
        return out.toByteArray();
    }

    private void validateContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidScreenshotException(
                "Payment screenshot must be an image file (PNG, JPEG, WebP, GIF). " +
                "Received: " + contentType);
        }
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidScreenshotException(
                "Payment screenshot must be smaller than 5 MB. " +
                "Received: " + (file.getSize() / 1024 / 1024) + " MB");
        }
    }
}
