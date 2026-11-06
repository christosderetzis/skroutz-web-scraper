@ApplicationModule(
        id= "search",
        displayName = "Search Module",
        allowedDependencies = {"category", "category :: schema", "product"})
package org.skroutz.scraper.skroutzwebscraper.search;

import org.springframework.modulith.ApplicationModule;