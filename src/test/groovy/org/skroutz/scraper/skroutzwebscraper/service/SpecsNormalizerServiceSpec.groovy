package org.skroutz.scraper.skroutzwebscraper.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.skroutz.scraper.skroutzwebscraper.schema.CategoryMappingSchema
import org.skroutz.scraper.skroutzwebscraper.schema.DirectFieldMapping
import org.skroutz.scraper.skroutzwebscraper.schema.FeatureExtraction
import org.skroutz.scraper.skroutzwebscraper.schema.FeatureFieldMapping
import org.skroutz.scraper.skroutzwebscraper.schema.FieldType
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import spock.lang.Specification
import spock.lang.Subject

class SpecsNormalizerServiceSpec extends Specification {

    @Subject
    SpecsNormalizerService service = new SpecsNormalizerService()

    def "Happy Path, normalize specifications with valid input"() {
        given: "a JSON node with specifications"
            CategoryMappingSchema schema = CategoryMappingSchema.builder()
                    .directFields(List.of(
                            DirectFieldMapping.builder().path("Main Specifications.Mobile Phone Type").target("mobile_phone_type").type(FieldType.STRING).build(),
                            DirectFieldMapping.builder().path("Main Specifications.Operating System").target("operating_system").type(FieldType.STRING).build(),
                            DirectFieldMapping.builder().path("Main Specifications.Release Year").target("release_year").type(FieldType.INTEGER).build(),
                            DirectFieldMapping.builder().path("Main Specifications.Colour").target("colour").type(FieldType.STRING).build(),
                            DirectFieldMapping.builder().path("Processor & Memory.Processor Model").target("processor_model").type(FieldType.STRING).build(),
                            DirectFieldMapping.builder().path("Processor & Memory.RAM").target("ram_gb").type(FieldType.NUMERIC).build(),
                            DirectFieldMapping.builder().path("Processor & Memory.Capacity").target("storage_gb").type(FieldType.NUMERIC).build(),
                            DirectFieldMapping.builder().path("Display.Size").target("display_inches").type(FieldType.NUMERIC).build(),
                            DirectFieldMapping.builder().path("Display.Analysis").target("display_analysis").type(null).build(),
                            DirectFieldMapping.builder().path("Display.Refresh Rate").target("refresh_rate_hz").type(FieldType.NUMERIC).build(),
                            DirectFieldMapping.builder().path("Display.Maximum Brightness").target("maximum_brightness_nits").type(FieldType.NUMERIC).build(),
                            DirectFieldMapping.builder().path("Battery.Capacity").target("battery_capacity_mah").type(FieldType.NUMERIC).build()
                    ))
                    .arrayFields(List.of(
                            FeatureFieldMapping.builder().path("Main Specifications.SIM").target("features").type(FeatureExtraction.VALUE).build(),
                            FeatureFieldMapping.builder().path("Processor & Memory.Card Slot").target("features").type(FeatureExtraction.YES_KEY).build(),
                            FeatureFieldMapping.builder().path("Network & Connectivity.Network Connection").target("features").type(null).build(),
                            FeatureFieldMapping.builder().path("Network & Connectivity.Connectivity").target("features").type(FeatureExtraction.COMMA_SPLIT).build(),
                            FeatureFieldMapping.builder().path("Network & Connectivity.NFC").target("features").type(FeatureExtraction.YES_KEY).build(),
                            FeatureFieldMapping.builder().path("Battery.Fast Charging").target("features").type(FeatureExtraction.YES_KEY).build(),
                            FeatureFieldMapping.builder().path("Special Features.Protection").target("features").type(FeatureExtraction.COMMA_SPLIT).build(),
                            FeatureFieldMapping.builder().path("AI Features").target("ai_features").type(FeatureExtraction.YES_GROUP).build()
                    ))
                    .build();
            String rawSpecs = """
                    {
                      "Camera": {
                        "Features": "Dual Pixel, Dual-tone Flash, HDR, Night Mode, OIS",
                        "Rear Camera": "Single",
                        "Rear Camera Flash": "Yes",
                        "Rear Camera Lenses": "Wide Angle 48MP",
                        "Selfie Camera Lenses": "Ευρυγώνιος 18MP",
                        "Rear Camera Video Resolution": "4K 24fps, 4K 30fps, 4K 60fps"
                      },
                      "Battery": {
                        "Capacity": "3149 mAh",
                        "Removable": "No",
                        "Fast Charging": "Yes",
                        "Wireless Charging Qi": "Yes",
                        "Wireless Charging Power": "20 W",
                        "Fast Charging Technology": "Power Delivery 2.0"
                      },
                      "Display": {
                        "Size": "6.5",
                        "Type": "Super Retina XDR OLED",
                        "Analysis": "2736 x 1260 pixels",
                        "Handling": "Touch screen",
                        "Definition": "Full HD",
                        "Refresh Rate": "120 Hz",
                        "Maximum Brightness": "3000 nits"
                      },
                      "Dimensions": {
                        "Width": "74.7 mm",
                        "Length": "156.2 mm",
                        "Thickness": "5.6 mm"
                      },
                      "AI Features": {
                        "AI Image Search": "Yes",
                        "AI Photo Editing": "Yes",
                        "AI Text Generation": "Yes",
                        "AI Call/Speech Translation": "Yes"
                      },
                      "Energy Label": {
                        "Energy Class": "A"
                      },
                      "Special Features": {
                        "Type": "Accelerometer, Face ID, Light Sensor, Proximity, Gyroscope, Compass",
                        "Protection": "Dust Resistant, Water Resistant",
                        "Fingerprint": "Without",
                        "Protection Certification": "IP68"
                      },
                      "Processor & Memory": {
                        "RAM": "12 GB",
                        "Capacity": "256 GB",
                        "Card Slot": "No",
                        "Processor Cores": "2+4",
                        "Processor Model": "Apple A19 Pro"
                      },
                      "Main Specifications": {
                        "SIM": "eSIM",
                        "Colour": "Gold",
                        "Weight": "165 gr",
                        "Release Year": "2025",
                        "Operating System": "iOS",
                        "Package Contents": "Charging Cable",
                        "Mobile Phone Type": "SmartPhone"
                      },
                      "Network & Connectivity": {
                        "NFC": "Yes",
                        "Connectivity": "Bluetooth, USB-C, Wi-Fi",
                        "Network Connection": "5G"
                      }
                    }
                """
            String cleanSpecs = """
                    {
                      "colour": "Gold",
                      "ram_gb": 12,
                      "features": [
                        "eSIM",
                        "5G",
                        "Bluetooth",
                        "USB-C",
                        "Wi-Fi",
                        "NFC",
                        "Fast Charging",
                        "Dust Resistant",
                        "Water Resistant"
                      ],
                      "storage_gb": 256,
                      "ai_features": [
                        "AI Photo Editing",
                        "AI Text Generation",
                        "AI Call/Speech Translation",
                        "AI Image Search"
                      ],
                      "release_year": 2025,
                      "display_inches": 6.5,
                      "processor_model": "Apple A19 Pro",
                      "refresh_rate_hz": 120,
                      "display_analysis": "2736 x 1260 pixels",
                      "operating_system": "iOS",
                      "mobile_phone_type": "SmartPhone",
                      "battery_capacity_mah": 3149,
                      "maximum_brightness_nits": 3000
                    }
                """
        ObjectMapper mapper = new ObjectMapper()
        JsonNode inputNode = mapper.readTree(rawSpecs)


    when: "normalizing specifications"
        String resultNode = service.normalize(inputNode, schema)

    then: "the output should be normalized correctly"
        JSONAssert.assertEquals(resultNode, cleanSpecs, JSONCompareMode.LENIENT)
    }
}
