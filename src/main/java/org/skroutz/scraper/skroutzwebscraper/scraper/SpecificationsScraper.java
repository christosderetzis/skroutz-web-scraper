package org.skroutz.scraper.skroutzwebscraper.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class SpecificationsScraper extends AbstractScraper {

    private final ObjectMapper mapper = new ObjectMapper();

    public SpecificationsScraper(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    public JsonNode scrapeSpecifications(String url) {
        return executeWithWebDriver(webDriver -> {
            webDriver.get(url);

            String renderedHtml = webDriver.getPageSource();
            return parseSpecifications(renderedHtml);
        }, url, "scraping specifications");
    }

    private JsonNode parseSpecifications(String htmlPage) {
        Document document = Jsoup.parse(htmlPage);
        Elements specGroups = document.select(HtmlFields.SPECIFICATIONS);
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
                    if (dt.isEmpty() || dd.isEmpty()) continue; // <-- skip empty entries

                    categoryNode.put(dt, dd);
                } catch (Exception e) {
                    log.debug("Skipping malformed specification entry: {}", e.getMessage());
                }
            }
            rootNode.set(category, categoryNode);
        }
        return rootNode;
    }
}
