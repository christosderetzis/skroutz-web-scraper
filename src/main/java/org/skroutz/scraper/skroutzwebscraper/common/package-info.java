@ApplicationModule(type = ApplicationModule.Type.OPEN, id="common", displayName = "Common Module", allowedDependencies = {"category", "product", "scraping", "review", "priceHistory"})
package org.skroutz.scraper.skroutzwebscraper.common;

import org.springframework.modulith.ApplicationModule;