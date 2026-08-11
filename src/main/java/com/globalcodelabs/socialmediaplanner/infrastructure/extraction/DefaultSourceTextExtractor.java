package com.globalcodelabs.socialmediaplanner.infrastructure.extraction;

import com.globalcodelabs.socialmediaplanner.domain.enums.ContentSourceType;
import com.globalcodelabs.socialmediaplanner.infrastructure.storage.LocalDocumentStorage;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class DefaultSourceTextExtractor {

    private static final String USER_AGENT = "ai-social-media-content-planner/1.0";
    private static final int MAX_REDIRECTS = 5;

    private final LocalDocumentStorage documentStorage;
    private final ExtractionProperties properties;
    private final OkHttpClient httpClient;

    public DefaultSourceTextExtractor(
            LocalDocumentStorage documentStorage,
            ExtractionProperties properties
    ) {
        this.documentStorage = documentStorage;
        this.properties = properties;
        long timeoutMillis = properties.getLinkTimeout().toMillis();
        this.httpClient = new OkHttpClient.Builder()
                .dns(this::resolvePublicAddresses)
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
    }

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
        URI currentUri = validateHttpUrl(sourceValue);
        try {
            for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
                Request request = new Request.Builder()
                        .url(currentUri.toString())
                        .header("User-Agent", USER_AGENT)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (isRedirect(response.code())) {
                        if (redirectCount == MAX_REDIRECTS) {
                            throw new IllegalStateException("Source link exceeded redirect limit");
                        }
                        String location = response.header("Location");
                        if (location == null || location.isBlank()) {
                            throw new IllegalStateException("Source link redirect is missing Location header");
                        }
                        currentUri = validateHttpUrl(currentUri.resolve(location).toString());
                        continue;
                    }
                    if (response.code() >= 400) {
                        throw new IllegalStateException(
                                "Source link returned HTTP status " + response.code()
                        );
                    }

                    validateHtmlContentType(response);
                    byte[] html = readLimitedBody(response);
                    Document document = Jsoup.parse(
                            new ByteArrayInputStream(html),
                            null,
                            currentUri.toString()
                    );
                    document.select("script, style, noscript, nav, footer").remove();
                    return document.body().text();
                }
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

    private URI validateHttpUrl(String sourceValue) {
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
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Source link must not contain user information");
        }
        try {
            resolvePublicAddresses(uri.getHost());
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
        return uri;
    }

    private List<InetAddress> resolvePublicAddresses(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = Arrays.asList(InetAddress.getAllByName(hostname));
        if (addresses.isEmpty()) {
            throw new UnknownHostException("Source link host could not be resolved");
        }
        if (addresses.stream().anyMatch(this::isPrivateAddress)) {
            throw new UnknownHostException("Private network links are not allowed");
        }
        return List.copyOf(addresses);
    }

    private void validateHtmlContentType(Response response) {
        String contentType = response.header("Content-Type");
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalStateException("Source link response Content-Type is missing");
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!"text/html".equals(mediaType) && !"application/xhtml+xml".equals(mediaType)) {
            throw new IllegalStateException("Source link returned unsupported Content-Type " + mediaType);
        }
    }

    private byte[] readLimitedBody(Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            throw new IllegalStateException("Source link response body is missing");
        }
        int maximumSize = properties.getMaxBodySizeBytes();
        if (body.contentLength() > maximumSize) {
            throw new IllegalStateException("Source link response exceeded body size limit");
        }

        try (InputStream input = body.byteStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumSize, 8192))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > maximumSize - total) {
                    throw new IllegalStateException("Source link response exceeded body size limit");
                }
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toByteArray();
        }
    }

    private boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    private boolean isPrivateAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isBlockedIpv4(bytes, 0);
        }
        if (isIpv4MappedIpv6(bytes)) {
            return isBlockedIpv4(bytes, 12);
        }
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address)
                || isDocumentationIpv6(bytes);
    }

    private boolean isBlockedIpv4(byte[] bytes, int offset) {
        int first = Byte.toUnsignedInt(bytes[offset]);
        int second = Byte.toUnsignedInt(bytes[offset + 1]);
        int third = Byte.toUnsignedInt(bytes[offset + 2]);
        return first == 0
                || first == 10
                || (first == 100 && second >= 64 && second <= 127)
                || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 0 && third == 0)
                || (first == 192 && second == 0 && third == 2)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113)
                || first >= 224;
    }

    private boolean isIpv4MappedIpv6(byte[] bytes) {
        if (bytes.length != 16 || bytes[10] != (byte) 0xFF || bytes[11] != (byte) 0xFF) {
            return false;
        }
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private boolean isDocumentationIpv6(byte[] bytes) {
        return bytes.length == 16
                && bytes[0] == 0x20
                && bytes[1] == 0x01
                && bytes[2] == 0x0D
                && (bytes[3] & 0xFF) == 0xB8;
    }
}
