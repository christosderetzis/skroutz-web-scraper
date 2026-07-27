package org.skroutz.scraper.skroutzwebscraper.scraping.application.service

import org.skroutz.scraper.skroutzwebscraper.scraping.domain.entity.ScrapeJob
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobStatus
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobType
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.repository.ScrapeJobRepository
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScrapeJobResponseDto
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception.JobNotFoundException
import org.springframework.test.util.ReflectionTestUtils
import spock.lang.Specification

import java.time.LocalDateTime

class ScrapeJobServiceSpec extends Specification {

    ScrapeJobRepository repository = Mock()
    ScrapeJobService service

    def setup() {
        service = new ScrapeJobService(repository)
        // @Value is not processed outside Spring — set the threshold explicitly
        ReflectionTestUtils.setField(service, "staleThresholdHours", 2)
    }

    def "failJob marks job as FAILED with error message"() {
        given:
            def jobId = UUID.randomUUID()
            def job = ScrapeJob.builder()
                    .id(jobId)
                    .status(ScrapeJobStatus.RUNNING)
                    .startedAt(LocalDateTime.now())
                    .build()
            repository.findById(jobId) >> Optional.of(job)

        when:
            service.failJob(jobId, "connection timeout")

        then:
            1 * repository.save({ it.status == ScrapeJobStatus.FAILED && it.error == "connection timeout" })
    }

    def "getJob returns the DTO when found"() {
        given:
            def jobId = UUID.randomUUID()
            def job = ScrapeJob.builder().id(jobId).status(ScrapeJobStatus.COMPLETED).jobType(ScrapeJobType.SCRAPE_PRODUCTS).build()
            repository.findById(jobId) >> Optional.of(job)

        when:
            def result = service.getJob(jobId)

        then:
            result instanceof ScrapeJobResponseDto
            with(result) {
                id == jobId
                status == ScrapeJobStatus.COMPLETED.name()
                jobType == ScrapeJobType.SCRAPE_PRODUCTS.name()
            }
    }

    def "getJob throws JobNotFoundException when job does not exist"() {
        given:
            def jobId = UUID.randomUUID()
            repository.findById(jobId) >> Optional.empty()

        when:
            service.getJob(jobId)

        then:
            thrown(JobNotFoundException)
    }
}
