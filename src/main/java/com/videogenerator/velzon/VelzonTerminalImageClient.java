package com.videogenerator.velzon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Velzon'un kendi sunucusunda, sembol başına ÜCRETSİZ ve JS-render
 * BEKLEMEDEN üretilen sosyal medya kartını (1200×630 PNG, gerçek fiyat +
 * mum grafiği + Velzon markası) indirir — {@code GET
 * /teknik-skorlar/{symbol}/og-image.png}. Bu, "AI ile soyut bir finansal
 * dashboard görseli üret" yaklaşımının yerine geçti (2026-08-24 kullanıcı
 * kararı: gerçek terminal görseli daha özgün ve ücretsiz) — üçüncü parti
 * bir ekran görüntüsü servisine (ScreenshotOne vb.) de bilinçli olarak
 * gerek duyulmadı, çünkü bu uç zaten var ve tam istenen sonucu veriyor.
 */
public class VelzonTerminalImageClient {
    private static final Logger logger = LoggerFactory.getLogger(VelzonTerminalImageClient.class);

    public interface Http {
        byte[] getBytes(String url) throws Exception;
    }

    private final Http http;
    private final String baseUrl;

    public VelzonTerminalImageClient(Http http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    public File fetch(String symbol, Path outFile) throws Exception {
        String url = baseUrl + "/teknik-skorlar/" + urlEnc(symbol) + "/og-image.png";
        byte[] bytes = http.getBytes(url);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Velzon terminal image: empty response for " + symbol);
        }
        Files.createDirectories(outFile.getParent());
        Files.write(outFile, bytes);
        logger.info("Terminal image fetched for {}: {} bytes", symbol, bytes.length);
        return outFile.toFile();
    }

    private static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
