package com.pharma.medicinestock.service;

import com.pharma.medicinestock.exception.InvalidScreenshotException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
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
    private static final int  MAX_DIMENSION = 900;           // px on longest side
    private static final int  MAX_DECODE_DIMENSION = 8000;   // reject before allocating a buffer this large
    private static final float JPEG_QUALITY = 0.65f;

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
        validateRealFormat(file.getBytes());
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
                byte[] bytes = file.getBytes();
                // The real MIME type is derived from the file's own magic bytes, never trusted
                // from the client-supplied Content-Type header — see validateRealFormat().
                String realMimeType = validateRealFormat(bytes);
                result.add(compressAndEncode(bytes, realMimeType));
            }
        }
        return result;
    }

    /**
     * Compresses raw image bytes and returns {@code [base64Data, mimeType]}.
     * {@code realMimeType} must already be confirmed (via {@link #validateRealFormat}) to match
     * the file's actual magic bytes — this method no longer falls back to storing bytes whose
     * format couldn't be confirmed.
     */
    public String[] compressAndEncode(byte[] rawBytes, String realMimeType) {
        // Preserve GIFs as-is (may be animated) — already confirmed via magic bytes by the caller
        if ("image/gif".equalsIgnoreCase(realMimeType)) {
            return new String[]{Base64.getEncoder().encodeToString(rawBytes), realMimeType};
        }
        // WebP isn't decodable by standard Java ImageIO, but its magic bytes are already
        // confirmed genuine — store as-is rather than attempting (and failing) to decode.
        if ("image/webp".equalsIgnoreCase(realMimeType)) {
            return new String[]{Base64.getEncoder().encodeToString(rawBytes), realMimeType};
        }
        try {
            rejectIfDimensionsExceedLimit(rawBytes);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (img == null) {
                throw new InvalidScreenshotException("Payment screenshot could not be decoded as a valid image.");
            }
            img = resizeIfNeeded(img);
            byte[] compressed = encodeAsJpeg(img);
            return new String[]{Base64.getEncoder().encodeToString(compressed), "image/jpeg"};
        } catch (InvalidScreenshotException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidScreenshotException("Payment screenshot could not be processed: " + e.getMessage());
        }
    }

    /**
     * Reads only the image header (not the full pixel data) to reject grossly oversized
     * declared dimensions before any full-size BufferedImage is allocated — guards against a
     * small file declaring huge width/height to exhaust server memory on decode.
     */
    private void rejectIfDimensionsExceedLimit(byte[] rawBytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return; // let the normal decode path handle/reject this
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w > MAX_DECODE_DIMENSION || h > MAX_DECODE_DIMENSION) {
                    throw new InvalidScreenshotException(
                        "Payment screenshot dimensions are too large (" + w + "x" + h + ").");
                }
            } finally {
                reader.dispose();
            }
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

    /**
     * Confirms the file's actual bytes match one of the allowed image formats via magic-byte
     * signatures — the client-supplied Content-Type header (checked by validateContentType) is
     * trivially spoofable and must never be trusted to decide what gets stored/served back.
     * Returns the real MIME type derived from the signature, to be used instead of the header.
     */
    private String validateRealFormat(byte[] bytes) {
        String detected = detectRealFormat(bytes);
        if (detected == null) {
            throw new InvalidScreenshotException(
                "Payment screenshot content does not match a supported image format (PNG, JPEG, WebP, GIF).");
        }
        return detected;
    }

    private String detectRealFormat(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return "image/gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
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
