package org.skroutz.scraper.skroutzwebscraper.scraping.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SpecificationsScraper extends AbstractScraper {

    private static final Pattern NUMERIC_WITH_UNIT = Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s*([a-zA-Zα-ωΑ-Ωά-ώΆ-Ώ]+)?$");

    private final ObjectMapper mapper = new ObjectMapper();

    public SpecificationsScraper(ApplicationContext applicationContext, @Value("${scraper.base-url}") String baseUrl) {
        super(applicationContext, baseUrl);
    }

    public JsonNode scrapeSpecifications(String url) {
        return executeWithWebDriver(webDriver -> {
            webDriver.get(url);
            String renderedHtml = webDriver.getPageSource();
            return parseSpecifications(renderedHtml);
        }, url, "scraping specifications");
    }

    JsonNode parseSpecifications(String htmlPage) {
        Document document = Jsoup.parse(htmlPage);
        Elements specGroups = document.select(HtmlFields.SPECIFICATIONS);
        ObjectNode rootNode = mapper.createObjectNode();

        for (Element group : specGroups) {
            Element categoryElement = group.selectFirst("h3");
            if (categoryElement == null) continue;

            String category = categoryElement.text();
            ArrayNode categoryArray = mapper.createArrayNode();

            Elements dls = group.select("dl");
            for (Element dl : dls) {
                try {
                    Element dtElement = dl.selectFirst("dt");
                    Element ddElement = dl.selectFirst("dd");
                    if (dtElement == null || ddElement == null) continue;

                    String dt = dtElement.text().trim();
                    String dd = ddElement.text().replace("\"", "").trim();
                    if (dt.isEmpty() || dd.isEmpty()) continue;

                    categoryArray.add(buildSpecNode(dt, dd));
                } catch (Exception e) {
                    log.debug("Skipping malformed specification entry: {}", e.getMessage());
                }
            }
            rootNode.set(category, categoryArray);
        }
        return rootNode;
    }

    private ObjectNode buildSpecNode(String key, String value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("key", key);

        Matcher matcher = NUMERIC_WITH_UNIT.matcher(value);
        if (matcher.matches()) {
            String numericPart = matcher.group(1).replace(",", ".");
            double numericValue = Double.parseDouble(numericPart);
            if (numericValue == Math.floor(numericValue)) {
                node.put("value", (long) numericValue);
            } else {
                node.put("value", numericValue);
            }
            String unit = matcher.group(2);
            if (unit != null && !unit.isEmpty()) {
                node.put("unit", unit);
            }
        } else {
            node.put("value", value);
        }

        return node;
    }
}
