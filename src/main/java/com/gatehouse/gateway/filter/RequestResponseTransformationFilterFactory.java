package com.gatehouse.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class RequestResponseTransformationFilterFactory
        extends AbstractGatewayFilterFactory<RequestResponseTransformationFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseTransformationFilterFactory.class);

    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory;
    private final ObjectMapper objectMapper;

    public RequestResponseTransformationFilterFactory(
            ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilterFactory,
            ObjectMapper objectMapper) {
        super(Config.class);
        this.modifyResponseBodyFilterFactory = modifyResponseBodyFilterFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        Set<String> fieldsToMask = new HashSet<>();
        if (config.getMaskFields() != null && !config.getMaskFields().isBlank()) {
            Arrays.stream(config.getMaskFields().split(","))
                    .map(String::trim)
                    .map(f -> f.startsWith("$.") ? f.substring(2) : f)
                    .forEach(fieldsToMask::add);
        }

        // Configure ModifyResponseBodyGatewayFilterFactory delegate
        var bodyFilterConfig = new ModifyResponseBodyGatewayFilterFactory.Config();
        bodyFilterConfig.setInClass(String.class);
        bodyFilterConfig.setOutClass(String.class);
        bodyFilterConfig.setRewriteFunction(String.class, String.class, (exchange, originalBody) -> {
            log.info("RequestResponseTransformation rewriteFunction called! Body: {}, fieldsToMask: {}", originalBody, fieldsToMask);
            if (originalBody == null || originalBody.isBlank() || fieldsToMask.isEmpty()) {
                return Mono.justOrEmpty(originalBody);
            }

            MediaType contentType = exchange.getResponse().getHeaders().getContentType();
            log.info("Response Content-Type: {}", contentType);
            if (contentType != null && !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                return Mono.just(originalBody);
            }

            try {
                JsonNode root = objectMapper.readTree(originalBody);
                maskFieldsRecursively(root, fieldsToMask);
                String maskedBody = objectMapper.writeValueAsString(root);
                log.info("Masked body result: {}", maskedBody);
                return Mono.just(maskedBody);
            } catch (Exception e) {
                log.warn("Failed to parse/mask response body: {}", e.getMessage());
                return Mono.just(originalBody);
            }
        });

        GatewayFilter bodyFilter = modifyResponseBodyFilterFactory.apply(bodyFilterConfig);

        return new org.springframework.cloud.gateway.filter.OrderedGatewayFilter((exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            log.info("RequestResponseTransformationFilter called for URI: {}", request.getURI());

            // 1. Correlation ID
            String requestId = exchange.getAttribute(JwtValidationGlobalFilter.ATTR_REQUEST_ID);
            if (requestId == null) {
                requestId = request.getHeaders().getFirst("X-Request-Id");
                if (requestId == null || requestId.isBlank()) {
                    requestId = UUID.randomUUID().toString();
                }
            }
            exchange.getResponse().getHeaders().set("X-Request-Id", requestId);

            // 2. Rewrite X-Forwarded-* headers
            var remoteAddress = request.getRemoteAddress();
            String clientIp = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "127.0.0.1";
            String host = request.getHeaders().getFirst("Host");
            String proto = request.getURI().getScheme();

            ServerHttpRequest.Builder reqBuilder = request.mutate()
                    .header("X-Request-Id", requestId)
                    .header("X-Forwarded-For", clientIp)
                    .header("X-Forwarded-Proto", proto != null ? proto : "http");

            if (host != null) {
                reqBuilder.header("X-Forwarded-Host", host);
            }

            ServerHttpRequest mutatedRequest = reqBuilder.build();

            return bodyFilter.filter(exchange.mutate().request(mutatedRequest).build(), chain);
        }, org.springframework.cloud.gateway.filter.NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1);
    }

    private void maskFieldsRecursively(JsonNode node, Set<String> fieldsToMask) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();

                if (fieldsToMask.contains(fieldName) && value.isValueNode()) {
                    String maskedValue = maskValue(fieldName, value.asText());
                    objectNode.set(fieldName, new TextNode(maskedValue));
                } else if (value.isContainerNode()) {
                    maskFieldsRecursively(value, fieldsToMask);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode child : arrayNode) {
                maskFieldsRecursively(child, fieldsToMask);
            }
        }
    }

    private String maskValue(String fieldName, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "******";
        }
        if (fieldName.toLowerCase().contains("card") && rawValue.length() >= 4) {
            String lastFour = rawValue.substring(rawValue.length() - 4);
            return "****-****-****-" + lastFour;
        }
        return "******";
    }

    public static class Config {
        private String maskFields;

        public Config() {}

        public String getMaskFields() {
            return maskFields;
        }

        public void setMaskFields(String maskFields) {
            this.maskFields = maskFields;
        }
    }
}
