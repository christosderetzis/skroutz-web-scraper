package org.skroutz.scraper.skroutzwebscraper.scraper;

public class ProductWebCssFields {

    public static final String LISTING_CONTAINER = "ol";
    
    public static final String PRODUCT_ITEM_XPATH = "li[contains(@class,'cf') and contains(@class,'card')]";
    
    public static final String PRODUCT_LINK = "a.js-sku-link";
    
    public static final String PRICE_LINK = "a[data-e2e-testid='sku-price-link']";
    
    public static final String IMAGE_CONTAINER = "div.image-container img";
    
    public static final String DESCRIPTION = "p.specs";
    
    public static final String RATING = "div.rating-wrapper span[data-testid='star-rating-value']";

    public static final String PAGINATION_BUTTON = ".paginator button span";

    public static final String SPECIFICATIONS = "#specs > div.specs-container.content.section > div.spec-groups > div.spec-details";

}
