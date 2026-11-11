package org.skroutz.scraper.skroutzwebscraper.common.infrastructure.events;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransactionalEventPublisher {

    private final ApplicationEventPublisher delegate;

    @Transactional
    public void publish(Object event) {
        delegate.publishEvent(event);
    }
}
