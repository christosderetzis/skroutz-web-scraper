package org.skroutz.scraper.skroutzwebscraper.utils;

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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SpecificationsNormalizerUtils {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^(\\d+(?:[.,]\\d+)?)");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String normalize(JsonNode rawSpecs, CategoryMappingSchema schema) {
        ObjectNode result = objectMapper.createObjectNode();

        // 1. Process Direct Fields
        for (DirectFieldMapping mapping : schema.getDirectFields()) {
            JsonNode value = resolvePath(rawSpecs, mapping.getPath());
            if (value.isMissingNode() || value.isNull()) continue;

            processDirectField(result, mapping, value);
        }

        // 2. Process Array Fields
        Map<String, ArrayNode> arrayGroups = new LinkedHashMap<>();

        for (FeatureFieldMapping mapping : schema.getArrayFields()) {
            ArrayNode node = arrayGroups.computeIfAbsent(mapping.getTarget(), k -> objectMapper.createArrayNode());
            processArrayField(rawSpecs, mapping, node);
        }

        arrayGroups.forEach(result::set);
        return result.toString();
    }

    private void processDirectField(ObjectNode result, DirectFieldMapping mapping, JsonNode value) {
        FieldType type = mapping.getType() == null ? FieldType.STRING : mapping.getType();
        String target = mapping.getTarget();

        switch (type) {
            case INTEGER -> result.put(target, value.asInt());
            case NUMERIC -> {
                String text = value.asText();
                parseAndPutNumeric(result, target, text);
            }
            default -> result.put(target, value.asText());
        }
    }

    private void processArrayField(JsonNode rawSpecs, FeatureFieldMapping mapping, ArrayNode targetArray) {
        JsonNode sourceNode = resolvePath(rawSpecs, mapping.getPath());
        if (sourceNode.isMissingNode() || sourceNode.isNull()) return;

        FeatureExtraction mode = mapping.getType() == null ? FeatureExtraction.VALUE : mapping.getType();

        switch (mode) {
            case YES_GROUP -> {
                sourceNode.properties().forEach(entry -> {
                    if (isYes(entry.getValue())) {
                        targetArray.add(entry.getKey());
                    }
                });
            }
            case YES_KEY -> {
                if (isYes(sourceNode)) {
                    String[] pathParts = mapping.getPath().split("\\.");
                    targetArray.add(pathParts[pathParts.length - 1]);
                }
            }
            case COMMA_SPLIT -> {
                for (String s : sourceNode.asText().split(",")) {
                    String val = s.trim();
                    if (!val.isEmpty()) targetArray.add(val);
                }
            }
            default -> { // VALUE case
                if (!sourceNode.asText().isBlank()) {
                    targetArray.add(sourceNode.asText());
                }
            }
        }
    }

    private JsonNode resolvePath(JsonNode node, String path) {
        if (path == null || path.isEmpty()) return node;
        // Supports nested dots by converting to JSON Pointer
        return node.at("/" + path.replace(".", "/"));
    }

    private boolean isYes(JsonNode node) {
        return "Yes".equalsIgnoreCase(node.asText());
    }

    private void parseAndPutNumeric(ObjectNode result, String target, String raw) {
        Matcher matcher = NUMBER_PATTERN.matcher(raw.trim());

        String cleanRaw = raw.trim();
        if (cleanRaw.isEmpty() || "-".equals(cleanRaw) || "N/A".equalsIgnoreCase(cleanRaw)) {
            return;
        }
        if (matcher.find()) {
            try {
                String val = matcher.group(1).replace(",", ".");
                if (val.contains(".")) {
                    result.put(target, Double.parseDouble(val));
                } else {
                    result.put(target, Long.parseLong(val));
                }
                return;
            } catch (NumberFormatException ignored) { }
        }
        result.put(target, raw); // Fallback to raw string if parsing fails
    }
}
