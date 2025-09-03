package org.skroutz.scraper.skroutzwebscraper.utils.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class JsonFileReader {

    static JsonNode readJsonFromResource(String fileName) {
        ObjectMapper mapper = new ObjectMapper()

        InputStream is = Thread.currentThread().contextClassLoader.getResourceAsStream("json-files/${fileName}")
        if (!is) {
            println "Resource '${fileName}' not found!"
            return null
        }
        try {
            return mapper.readTree(is)
        } catch (Exception e) {
            e.printStackTrace()
            return null
        } finally {
            is.close()
        }
    }
}
