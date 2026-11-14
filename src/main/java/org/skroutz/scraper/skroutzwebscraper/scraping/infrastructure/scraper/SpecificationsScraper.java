package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class SpecificationsScraper {

    public static final String SPECIFICATIONS = "#specs > div.specs-container.content.section > div.spec-groups > div.spec-details";
    public static final String BRAND = "#description .manufacturer > div:first-of-type > a:first-of-type";

    private final ObjectMapper mapper = new ObjectMapper();
    private final FlareSolverrClient flareSolverrClient;

    public SpecificationsScraper() {
        this(null);
    }

    @Autowired
    public SpecificationsScraper(FlareSolverrClient flareSolverrClient) {
        this.flareSolverrClient = flareSolverrClient;
    }

    public Optional<JsonNode> scrapeSpecifications(String url) {
        try {
             Document document = fetchDocument(url);

            return Optional.of(parseSpecifications(document));
        } catch (HttpStatusException e) {
            log.warn("HTTP error fetching URL {}: {}", url, e.getStatusCode());
        } catch (Exception e) {
            log.error("Error scraping specifications from URL {}: {}", url, e.getMessage());
        }
        return Optional.empty();
    }

    private Document fetchDocument(String url) throws Exception {
        if (flareSolverrClient != null && flareSolverrClient.isEnabled()) {
            return Jsoup.parse(flareSolverrClient.fetchHtml(url), url);
        }

        return Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .referrer("https://www.google.com/")
                .timeout(10000)
                .get();
    }

    private JsonNode parseSpecifications(Document document) {
        Elements specGroups = document.select(SPECIFICATIONS);
        ObjectNode rootNode = mapper.createObjectNode();

        for (Element group : specGroups) {
            Element categoryElement = group.selectFirst("h3");
            if (categoryElement == null) continue;

            String category = categoryElement.text();
            ObjectNode categoryNode = mapper.createObjectNode();

            Elements dls = group.select("dl");
            for (Element dl : dls) {
                try {
                    Element dtElement = dl.selectFirst("dt");
                    Element ddElement = dl.selectFirst("dd");
                    if (dtElement == null || ddElement == null) continue;

                    String dt = dtElement.text().trim();
                    String dd = ddElement.text().replace("\"", "").trim();
                    if (dt.isEmpty() || dd.isEmpty()) continue;

                    categoryNode.put(dt, dd);
                } catch (Exception e) {
                    log.debug("Skipping malformed specification entry: {}", e.getMessage());
                }
            }
            rootNode.set(category, categoryNode);
        }

        Element brandElement = document.selectFirst(BRAND);
        if (brandElement != null) {
            rootNode.put("brand", brandElement.text().trim());
        }
        return rootNode;
    }
}
