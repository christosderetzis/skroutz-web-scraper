package org.skroutz.scraper.skroutzwebscraper.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SpecificationsScraper extends AbstractScraper {

    public SpecificationsScraper(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    public JsonNode scrapeSpecifications(String url) {
        return executeWithWebDriver(webDriver -> {
            webDriver.get(url);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return parseSpecifications(webDriver);
        }, url, "scraping specifications");
    }

    private JsonNode parseSpecifications(WebDriver webDriver) {
        List<WebElement> specGroups = webDriver.findElements(By.cssSelector(HtmlFields.SPECIFICATIONS));
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode rootNode = mapper.createObjectNode();

        for (WebElement group : specGroups) {
            String category = group.findElement(By.tagName("h3")).getText();
            ObjectNode categoryNode = mapper.createObjectNode();

            List<WebElement> dls = group.findElements(By.tagName("dl"));
            for (WebElement dl : dls) {
                try {
                    String dt = dl.findElement(By.tagName("dt")).getText();
                    String dd = dl.findElement(By.tagName("dd")).getText().replaceAll("\"", "");
                    categoryNode.put(dt, dd);
                } catch (Exception e) {
                    // Skip malformed dl if any
                    continue;
                }
            }
            rootNode.set(category, categoryNode);
        }
        return rootNode;
    }
}
