package org.skroutz.scraper.skroutzwebscraper.base

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.skroutz.scraper.skroutzwebscraper.SkroutzWebScraperApplication
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.util.function.Predicate

abstract class WithLoggingBaseSpec extends Specification {

    ListAppender<ILoggingEvent> appender

    def setup() {
        startLogAppender()
    }

    def cleanup() {
        appender && appender.list.clear()
    }

    void startLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(SkroutzWebScraperApplication.package.name)
        this.appender = new ListAppender<>()
        this.appender.context = (LoggerContext) LoggerFactory.getILoggerFactory()
        logger.addAppender(this.appender)
        this.appender.start()
    }

    boolean assertLog(Level level, String message) {
        return appender.list.any {
            it.getFormattedMessage().contains(message) && it.getLevel() == level
        }
    }

    boolean assertLog(Level level, Predicate<String>... predicates) {
        return appender.list.any(message -> {
            def array = predicates.collect { it.test(message.getFormattedMessage()) }
            def all = array.every { it }
            return message.getLevel() == level && all
        })
    }
}

