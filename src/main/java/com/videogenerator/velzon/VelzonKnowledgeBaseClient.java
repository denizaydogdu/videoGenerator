package com.videogenerator.velzon;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Velzon'un genel bilgi merkezi'nde (https://www.velzon.tr/bilgi-merkezi/)
 * yayınlanan GERÇEK makaleleri okur — sosyal medya içeriğinin serbestçe
 * uydurulmuş konular yerine Velzon'un kendi yayınlanmış bilgisine
 * dayandırılması için (bkz. VelzonTweetGenerator/VelzonInstagramPostGenerator/
 * VelzonYoutubeScriptGenerator javadoc'ları).
 *
 * Kimlik doğrulama gerekmez — bu herkese açık bir web sitesi.
 */
public class VelzonKnowledgeBaseClient {
    private static final Logger logger = LoggerFactory.getLogger(VelzonKnowledgeBaseClient.class);
    private static final String BASE_URL = "https://www.velzon.tr";
    private static final String INDEX_PATH = "/bilgi-merkezi/";

    // Yapısal kural: bir makale /bilgi-merkezi/<kategori>/<slug>/ şeklinde TAM
    // olarak 2 segment taşır. Kategori adını sabit listelemek YANLIŞ ÇIKTI —
    // canlı sitede en az 4 kategori var (borsa-terimleri, baslangic,
    // derecelendirmeler, indikatörler); yeni kategori eklenirse bu kural
    // otomatik kapsar. Kategori index linkleri (ör.
    // /bilgi-merkezi/borsa-terimleri/, tek segment) ve alakasız nav linkleri
    // (ör. /hakkimizda/, /bilgi-merkezi/ dışı) bu deseni EŞLEŞTİRMEZ.
    private static final Pattern ARTICLE_PATH = Pattern.compile(
            "^/bilgi-merkezi/[^/\"'?#]+/[^/\"'?#]+/?$");

    public interface Http {
        String get(String url) throws Exception;
    }

    /**
     * @param path    /bilgi-merkezi/... ile başlayan göreli yol (host YOK)
     * @param content listArticles() sonucunda boş string'dir (400 makaleyi
     *                tek tek indirmek çok pahalı) — tam içerik için
     *                fetchArticle(path) çağrılmalı
     */
    public record Article(String title, String path, String content) {
    }

    private final Http http;

    public VelzonKnowledgeBaseClient(Http http) {
        this.http = http;
    }

    /**
     * İndeks sayfasındaki tüm gerçek makale linklerini döner. Performans
     * nedeniyle içerik ÇEKMEZ — yalnızca başlık (anchor metni, yoksa slug'dan
     * türetilmiş) ve yol. Tam içerik için fetchArticle(path) kullanılmalı.
     */
    public List<Article> listArticles() throws Exception {
        String html = http.get(BASE_URL + INDEX_PATH);
        Document doc = Jsoup.parse(html, BASE_URL);
        Elements anchors = doc.select("a[href]");

        // LinkedHashMap: ilk görülen sırayı korur, path'e göre dedupe eder
        Map<String, String> titleByPath = new LinkedHashMap<>();
        for (Element a : anchors) {
            String path = normalizedPath(a.attr("href"));
            if (path == null || !ARTICLE_PATH.matcher(path).matches()) {
                continue;
            }
            if (titleByPath.containsKey(path)) {
                continue;
            }
            String anchorText = a.text().trim();
            titleByPath.put(path, anchorText.isEmpty() ? titleFromSlug(path) : anchorText);
        }

        List<Article> articles = new ArrayList<>();
        for (Map.Entry<String, String> e : titleByPath.entrySet()) {
            articles.add(new Article(e.getValue(), e.getKey(), ""));
        }
        logger.info("Velzon knowledge-base index parsed: {} articles", articles.size());
        return articles;
    }

    /** Tek bir makalenin tam başlığını ve düz metin gövdesini çeker. */
    public Article fetchArticle(String path) throws Exception {
        String url = BASE_URL + path;
        String html = http.get(url);
        Document doc = Jsoup.parse(html, url);

        String rawTitle = doc.title();
        String title = rawTitle.contains(" | ")
                ? rawTitle.substring(0, rawTitle.indexOf(" | ")).trim()
                : rawTitle.trim();

        Element content = doc.selectFirst(".kb-markdown-content");
        if (content == null) {
            throw new IllegalStateException(
                    "Velzon KB article at " + path + " is missing its content "
                            + "(.kb-markdown-content) — page structure may have changed");
        }

        return new Article(title, path, extractReadableText(content));
    }

    /** Tag'leri temizler, paragraf/başlık aralarını boş satırla korur. */
    private static String extractReadableText(Element content) {
        List<String> blocks = new ArrayList<>();
        for (Element child : content.children()) {
            String text = child.text().trim();
            if (!text.isEmpty()) {
                blocks.add(text);
            }
        }
        if (blocks.isEmpty()) {
            return content.text().trim();
        }
        return String.join("\n\n", blocks);
    }

    /** href'i host'suz, query/fragment'sız, kapanış slash'lı, decode edilmiş yola indirger. */
    private static String normalizedPath(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(href, StandardCharsets.UTF_8);
        } catch (Exception e) {
            decoded = href;
        }

        String path = decoded;
        int schemeIdx = path.indexOf("://");
        if (schemeIdx >= 0) {
            int slash = path.indexOf('/', schemeIdx + 3);
            if (slash < 0) {
                return null;
            }
            path = path.substring(slash);
        }
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int h = path.indexOf('#');
        if (h >= 0) {
            path = path.substring(0, h);
        }
        if (!path.isEmpty() && !path.endsWith("/")) {
            path = path + "/";
        }
        return path;
    }

    /** "hisse-skorlarini-rehberi" -> "Hisse Skorlarini Rehberi" gibi basit bir slug->başlık çevirimi. */
    private static String titleFromSlug(String path) {
        String slug = path.replaceAll("^/bilgi-merkezi/(borsa-terimleri|baslangic)/", "")
                .replaceAll("/$", "");
        StringBuilder sb = new StringBuilder();
        for (String part : slug.split("-")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
