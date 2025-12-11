package org.skroutz.scraper.skroutzwebscraper.scraper;

public class HtmlFields {

    public static final String LISTING_CONTAINER = "ol";
    
    public static final String PRODUCT_ITEM_XPATH = "li[contains(@class,'cf') and contains(@class,'card')]";
    
    public static final String PRODUCT_LINK = "a.js-sku-link";
    
    public static final String PRICE_LINK = "a[data-e2e-testid='sku-price-link']";
    
    public static final String IMAGE_CONTAINER = "div.image-container img";
    
    public static final String DESCRIPTION = "p.specs";
    
    public static final String RATING = "div.rating-wrapper span[data-testid='star-rating-value']";

    public static final String PAGINATION_BUTTON = ".paginator button span";

    public static final String SPECIFICATIONS = "#specs > div.specs-container.content.section > div.spec-groups > div.spec-details";

    // Reviews selectors
    public static final String REVIEWS_CONTAINER = "#reviews-container";

    public static final String REVIEWS_LIST = "#sku_reviews_list";

    public static final String REVIEW_ITEM = ".review-item";

    public static final String LOAD_MORE_REVIEWS = ".load-more-reviews";

    public static final String MERGED_REVIEW_INFO = ".merged-review-info";

    public static final String REVIEWER_NAME = "a.author";

    public static final String REVIEW_BODY = ".review-body p";

    public static final String REVIEW_DATE = ".permalink.js-review-permalink";

    public static final String VERIFICATION_MARK = ".verification-mark";

    public static final String HELPFULNESS_MESSAGE = ".helpfulness-message";

    public static final String PROS_LIST = "ul.icon.pros > li";

    public static final String NEUTRAL_LIST = "ul.icon.so-so > li";

    public static final String CONS_LIST = "ul.icon.cons > li";

}
