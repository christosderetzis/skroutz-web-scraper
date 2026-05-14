package org.skroutz.scraper.skroutzwebscraper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.schema.CategoryMappingSchema;
import org.skroutz.scraper.skroutzwebscraper.schema.DirectFieldMapping;
import org.skroutz.scraper.skroutzwebscraper.schema.FeatureExtraction;
import org.skroutz.scraper.skroutzwebscraper.schema.FeatureFieldMapping;
import org.skroutz.scraper.skroutzwebscraper.schema.FieldType;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SpecsNormalizerService {

    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+(?:[.,]\\d+)?)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String normalize(JsonNode rawSpecs, CategoryMappingSchema schema) {
        ObjectNode result = objectMapper.createObjectNode();
        applyDirectFields(rawSpecs, schema.getDirectFields(), result);
        applyArrayFields(rawSpecs, schema.getArrayFields(), result);
        return result.toString();
    }

    private void applyDirectFields(JsonNode rawSpecs, List<DirectFieldMapping> fields, ObjectNode result) {
        for (DirectFieldMapping field : fields) {

            // Support nested paths like "dimensions.width"
            String[] parts = field.getPath().split("\\.", 2);
            JsonNode value = rawSpecs.path(parts[0]).path(parts[1]);
            if (value.isMissingNode() || value.isNull()) continue;

            String text = value.asText();
            switch (field.getType() == null ? FieldType.STRING : field.getType()) {
                case INTEGER -> result.put(field.getTarget(), value.asInt());
                case NUMERIC -> putNumeric(result, field.getTarget(), text);
                default      -> result.put(field.getTarget(), text);
            }
        }
    }

    private void putNumeric(ObjectNode result, String target, String raw) {
        Matcher matcher = LEADING_NUMBER.matcher(raw.trim());
        if (!matcher.find()) {
            result.put(target, raw);
            return;
        }
        String numStr = matcher.group(1).replace(",", ".");
        if (numStr.contains(".")) {
            result.put(target, Double.parseDouble(numStr));
        } else {
            result.put(target, Long.parseLong(numStr));
        }
    }

    private void applyArrayFields(JsonNode rawSpecs, List<FeatureFieldMapping> arrayFields, ObjectNode result) {
        Map<String, ArrayNode> arrays = new LinkedHashMap<>();

        for (FeatureFieldMapping field : arrayFields) {
            ArrayNode arr = arrays.computeIfAbsent(field.getTarget(), k -> objectMapper.createArrayNode());

            FeatureExtraction type = field.getType() == null ? FeatureExtraction.VALUE : field.getType();
            if (type == FeatureExtraction.YES_GROUP) {
                collectYesGroup(rawSpecs.path(field.getPath()), arr);
            } else {
                String[] parts = field.getPath().split("\\.", 2);
                JsonNode value = rawSpecs.path(parts[0]).path(parts[1]);
                if (value.isMissingNode() || value.isNull()) continue;
                collectValue(parts[1], value.asText(), type, arr);
            }
        }

        arrays.forEach(result::set);
    }

    private void collectYesGroup(JsonNode group, ArrayNode arr) {
        if (group.isMissingNode()) return;
        group.properties().forEach(entry -> {
            if ("Yes".equals(entry.getValue().asText())) arr.add(entry.getKey());
        });
    }

    private void collectValue(String key, String text, FeatureExtraction type, ArrayNode arr) {
        switch (type) {
            case YES_KEY -> { if ("Yes".equals(text)) arr.add(key); }
            case COMMA_SPLIT -> {
                for (String part : text.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) arr.add(trimmed);
                }
            }
            default -> { if (!text.isBlank()) arr.add(text); }
        }
    }
}
