package org.skroutz.scraper.skroutzwebscraper.common.utils;

import java.sql.Timestamp;

public class DateTimeUtils {

    public static Timestamp convertEpochToTimestamp(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return new Timestamp(epochSeconds * 1000);
    }
}
