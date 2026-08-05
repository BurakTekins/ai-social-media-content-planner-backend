package com.globalcodelabs.socialmediaplanner.infrastructure.extraction;

import com.globalcodelabs.socialmediaplanner.application.port.out.extraction.SourceTextExtractor;
import com.globalcodelabs.socialmediaplanner.application.port.out.storage.DocumentStorage;
import com.globalcodelabs.socialmediaplanner.domain.model.ContentSourceType;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultSourceTextExtractor implements SourceTextExtractor {

    private static final String USER_AGENT = "ai-social-media-content-planner/1.0";
    private static final int MAX_REDIRECTS = 5;

    private final DocumentStorage documentStorage;
    private final ExtractionProperties properties;

    @Override
    public String extract(ContentSourceType sourceType, String sourceValue) {
        String extractedText = switch (sourceType) {
            case LINK -> extractLink(sourceValue);
            case DOCUMENT -> extractDocument(sourceValue);
        };
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("Source does not contain extractable text");
        }
        return extractedText.trim();
    }

    private String extractLink(String sourceValue) {
        URI currentUri = validatePublicHttpUrl(sourceValue);
        try {
            for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
                Connection.Response response = Jsoup.connect(currentUri.toString())
                        .userAgent(USER_AGENT)
                        .timeout(Math.toIntExact(properties.getLinkTimeout().toMillis()))
                        .maxBodySize(properties.getMaxBodySizeBytes())
                        .followRedirects(false)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .execute();

                if (isRedirect(response.statusCode())) {
                    if (redirectCount == MAX_REDIRECTS) {
                        throw new IllegalStateException("Source link exceeded redirect limit");
                    }
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        throw new IllegalStateException("Source link redirect is missing Location header");
                    }
                    currentUri = validatePublicHttpUrl(currentUri.resolve(location).toString());
                    continue;
                }
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException(
                            "Source link returned HTTP status " + response.statusCode()
                    );
                }

                Document document = response.parse();
                document.select("script, style, noscript, nav, footer").remove();
                return document.body().text();
            }
            throw new IllegalStateException("Source link exceeded redirect limit");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not extract text from link", exception);
        }
    }

    private String extractDocument(String storageKey) {
        byte[] content = documentStorage.read(storageKey);
        String extension = storageKey.substring(storageKey.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> extractPdf(content);
            case "docx" -> extractDocx(content);
            case "txt" -> new String(content, StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException("Unsupported stored document extension: " + extension);
        };
    }

    private String extractPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not extract text from PDF", exception);
        }
    }

    private String extractDocx(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            String paragraphs = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining(System.lineSeparator()));
            String tables = document.getTables().stream()
                    .map(this::extractTable)
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining(System.lineSeparator()));
            return String.join(System.lineSeparator(), paragraphs, tables).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not extract text from DOCX", exception);
        }
    }

    private String extractTable(XWPFTable table) {
        return table.getRows().stream()
                .map(row -> row.getTableCells().stream()
                        .map(cell -> cell.getText().trim())
                        .collect(Collectors.joining(" | ")))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private URI validatePublicHttpUrl(String sourceValue) {
        URI uri;
        try {
            uri = URI.create(sourceValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid source link", exception);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Source link must use HTTP or HTTPS");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Source link host is missing");
        }
        try {
            boolean privateAddress = Arrays.stream(InetAddress.getAllByName(uri.getHost()))
                    .anyMatch(this::isPrivateAddress);
            if (privateAddress) {
                throw new IllegalArgumentException("Private network links are not allowed");
            }
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Source link host could not be resolved", exception);
        }
        return uri;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    private boolean isPrivateAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address);
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
