package com.example.demo.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmTemplateRenderer {

    private static final Pattern FULL_PLACEHOLDER = Pattern.compile("^\\s*\\{\\{\\s*([\\w.]+)\\s*}}\\s*$");
    private static final Pattern EMBEDDED_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

    private final ObjectMapper objectMapper;

    public LlmTemplateRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode render(String template, Map<String, Object> variables) throws IOException {
        JsonNode templateNode = objectMapper.readTree(template);
        return resolveNode(templateNode, variables);
    }

    private JsonNode resolveNode(JsonNode node, Map<String, Object> variables) {
        if (node == null || node.isNull()) {
            return NullNode.instance;
        }
        if (node.isObject()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> objectNode.set(entry.getKey(), resolveNode(entry.getValue(), variables)));
            return objectNode;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            node.forEach(item -> arrayNode.add(resolveNode(item, variables)));
            return arrayNode;
        }
        if (node.isTextual()) {
            String text = node.asText();
            Matcher fullMatcher = FULL_PLACEHOLDER.matcher(text);
            if (fullMatcher.matches()) {
                return toJsonNode(resolveValue(variables, fullMatcher.group(1)));
            }
            return TextNode.valueOf(resolveEmbeddedText(text, variables));
        }
        return node.deepCopy();
    }

    private String resolveEmbeddedText(String template, Map<String, Object> variables) {
        Matcher matcher = EMBEDDED_PLACEHOLDER.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object value = resolveValue(variables, matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return NullNode.instance;
        }
        if (value instanceof Boolean bool) {
            return BooleanNode.valueOf(bool);
        }
        if (value instanceof Integer integer) {
            return IntNode.valueOf(integer);
        }
        if (value instanceof Long longValue) {
            return LongNode.valueOf(longValue);
        }
        if (value instanceof Double doubleValue) {
            return DoubleNode.valueOf(doubleValue);
        }
        if (value instanceof Float floatValue) {
            return DoubleNode.valueOf(floatValue.doubleValue());
        }
        if (value instanceof BigDecimal decimal) {
            return DoubleNode.valueOf(decimal.doubleValue());
        }
        return objectMapper.valueToTree(value);
    }

    @SuppressWarnings("unchecked")
    private Object resolveValue(Map<String, Object> variables, String path) {
        String[] parts = path.split("\\.");
        Object current = variables;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = ((Map<String, Object>) currentMap).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
