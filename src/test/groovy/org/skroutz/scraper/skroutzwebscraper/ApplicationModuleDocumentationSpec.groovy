package org.skroutz.scraper.skroutzwebscraper

import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class ApplicationModuleDocumentationSpec extends Specification {

    def "emits application module documentation as PlantUML"() {
        given: "the application modules"
            def modules = ApplicationModules.of(SkroutzWebScraperApplication)

        when: "the documentation is generated"
            new Documenter(modules)
                    .writeModulesAsPlantUml()
                    .writeIndividualModulesAsPlantUml()

        then: "the component diagram and per-module diagrams are written"
            Path output = Path.of("build", "spring-modulith-docs")
            Files.exists(output.resolve("components.puml"))
            Files.exists(output.resolve("module-product.puml"))
            Files.exists(output.resolve("module-search.puml"))
    }
}
