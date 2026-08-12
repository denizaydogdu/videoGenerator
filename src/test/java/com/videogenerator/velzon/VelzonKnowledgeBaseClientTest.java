package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class VelzonKnowledgeBaseClientTest {

    private static final String INDEX_HTML = """
            <html><head><title>Bilgi Merkezi | Velzon</title></head>
            <body>
              <nav>
                <a href="/bilgi-merkezi/">Bilgi Merkezi</a>
                <a href="/bilgi-merkezi/borsa-terimleri/">Borsa Terimleri</a>
                <a href="/hakkimizda/">Hakkımızda</a>
              </nav>
              <ul>
                <li><a href="/bilgi-merkezi/borsa-terimleri/acente-nedir/">Acente Nedir?</a></li>
                <li><a href="/bilgi-merkezi/borsa-terimleri/acente-nedir/">Acente Nedir?</a></li>
                <li><a href="/bilgi-merkezi/baslangic/velzon-endeksler-rehberi/">Endeksler Rehberi</a></li>
                <li><a href="/bilgi-merkezi/baslangic/hisse-skorlar%C4%B1-rehberi/"></a></li>
                <li><a href="/bilgi-merkezi/derecelendirmeler/rs-rating/">RS Rating</a></li>
                <li><a href="/bilgi-merkezi/indikat%C3%B6rler/rsi/">RSI</a></li>
              </ul>
            </body></html>
            """;

    private static final String ARTICLE_HTML = """
            <html><head><title>Acente Nedir? | Bilgi Merkezi | Velzon</title></head>
            <body>
              <div class="kb-content">
                <div class="kb-markdown-content">
                  <h3>Acente Nedir?</h3>
                  <p>Acente, borsada aracılık faaliyeti yürüten kurumdur.</p>
                  <p>Yatırımcılar adına emir iletir.</p>
                  <ul><li>Madde 1</li><li>Madde 2</li></ul>
                </div>
              </div>
            </body></html>
            """;

    private static final String MALFORMED_ARTICLE_HTML = """
            <html><head><title>Bozuk Sayfa | Bilgi Merkezi | Velzon</title></head>
            <body><div class="not-the-right-class">nothing here</div></body></html>
            """;

    private VelzonKnowledgeBaseClient.Http fakeHttp(Map<String, String> responses) {
        return url -> {
            if (!responses.containsKey(url)) {
                throw new IllegalStateException("Unexpected URL requested: " + url);
            }
            return responses.get(url);
        };
    }

    @Test
    void listArticlesParsesDedupesAndDecodesTurkishSlugs() throws Exception {
        VelzonKnowledgeBaseClient client = new VelzonKnowledgeBaseClient(
                fakeHttp(Map.of("https://www.velzon.tr/bilgi-merkezi/", INDEX_HTML)));

        List<VelzonKnowledgeBaseClient.Article> articles = client.listArticles();

        List<String> paths = articles.stream().map(VelzonKnowledgeBaseClient.Article::path).toList();

        // Real articles only — category index links and unrelated nav links filtered out
        assertFalse(paths.contains("/bilgi-merkezi/"));
        assertFalse(paths.contains("/bilgi-merkezi/borsa-terimleri/"));
        assertFalse(paths.contains("/hakkimizda/"));

        // Deduped: the acente-nedir link appears twice in the HTML, once in the list
        long acenteCount = paths.stream().filter(p -> p.equals(
                "/bilgi-merkezi/borsa-terimleri/acente-nedir/")).count();
        assertEquals(1, acenteCount);

        // URL-encoded Turkish slug decoded (%C4%B1 -> dotless "ı")
        assertTrue(paths.contains("/bilgi-merkezi/baslangic/hisse-skorları-rehberi/"),
                "expected decoded Turkish slug in: " + paths);

        assertTrue(paths.contains("/bilgi-merkezi/baslangic/velzon-endeksler-rehberi/"));
    }

    @Test
    void listArticlesIncludesArticlesFromAnyCategoryNotJustHardcodedTwo() throws Exception {
        // Canlı sitede en az 4 kategori var (borsa-terimleri, baslangic,
        // derecelendirmeler, indikatörler) — kategori adını sabit listelemek
        // yerine yapısal kural (2 segment) kullanılmalı, aksi halde yeni
        // kategoriler sessizce dışlanır (bkz. adversarial review bulgusu).
        VelzonKnowledgeBaseClient client = new VelzonKnowledgeBaseClient(
                fakeHttp(Map.of("https://www.velzon.tr/bilgi-merkezi/", INDEX_HTML)));

        List<String> paths = client.listArticles().stream()
                .map(VelzonKnowledgeBaseClient.Article::path).toList();

        assertTrue(paths.contains("/bilgi-merkezi/derecelendirmeler/rs-rating/"),
                "expected derecelendirmeler category article in: " + paths);
        assertTrue(paths.contains("/bilgi-merkezi/indikatörler/rsi/"),
                "expected indikatörler category article (decoded) in: " + paths);
    }

    @Test
    void listArticlesUsesAnchorTextAsTitle() throws Exception {
        VelzonKnowledgeBaseClient client = new VelzonKnowledgeBaseClient(
                fakeHttp(Map.of("https://www.velzon.tr/bilgi-merkezi/", INDEX_HTML)));

        Map<String, String> titleByPath = client.listArticles().stream()
                .collect(Collectors.toMap(VelzonKnowledgeBaseClient.Article::path,
                        VelzonKnowledgeBaseClient.Article::title));

        assertEquals("Acente Nedir?",
                titleByPath.get("/bilgi-merkezi/borsa-terimleri/acente-nedir/"));
        assertEquals("Endeksler Rehberi",
                titleByPath.get("/bilgi-merkezi/baslangic/velzon-endeksler-rehberi/"));
    }

    @Test
    void listArticlesFallsBackToSlugDerivedTitleWhenAnchorTextEmpty() throws Exception {
        VelzonKnowledgeBaseClient client = new VelzonKnowledgeBaseClient(
                fakeHttp(Map.of("https://www.velzon.tr/bilgi-merkezi/", INDEX_HTML)));

        Map<String, String> titleByPath = client.listArticles().stream()
                .collect(Collectors.toMap(VelzonKnowledgeBaseClient.Article::path,
                        VelzonKnowledgeBaseClient.Article::title));

        String title = titleByPath.get("/bilgi-merkezi/baslangic/hisse-skorları-rehberi/");
        assertNotNull(title);
        assertFalse(title.isBlank());
        assertFalse(title.contains("-"));
    }

    @Test
    void listArticlesReturnsEmptyContentPlaceholder() throws Exception {
        VelzonKnowledgeBaseClient client = new VelzonKnowledgeBaseClient(
                fakeHttp(Map.of("https://www.velzon.tr/bilgi-merkezi/", INDEX_HTML)));

        for (VelzonKnowledgeBaseClient.Article a : client.listArticles()) {
            assertTrue(a.content() == null || a.content().isEmpty());
        }
    }

    @Test
    void fetchArticleParsesTitleAndBodyText() throws Exception {
        String path = "/bilgi-merkezi/borsa-terimleri/acente-nedir/";
        VelzonKnowledgeBaseClient client = new VelzonKnowledgeBaseClient(
                fakeHttp(Map.of("https://www.velzon.tr" + path, ARTICLE_HTML)));

        VelzonKnowledgeBaseClient.Article article = client.fetchArticle(path);

        assertEquals("Acente Nedir?", article.title());
        assertEquals(path, article.path());
        assertTrue(article.content().contains("aracılık faaliyeti"));
        assertTrue(article.content().contains("Yatırımcılar adına emir iletir."));
        assertFalse(article.content().contains("<p>"));
        assertFalse(article.content().contains("<div"));
    }

    @Test
    void fetchArticleThrowsClearExceptionWhenContentDivMissing() {
        String path = "/bilgi-merkezi/borsa-terimleri/bozuk/";
        VelzonKnowledgeBaseClient client = new VelzonKnowledgeBaseClient(
                fakeHttp(Map.of("https://www.velzon.tr" + path, MALFORMED_ARTICLE_HTML)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.fetchArticle(path));
        assertTrue(ex.getMessage().toLowerCase().contains("content"));
    }
}
