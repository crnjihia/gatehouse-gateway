package com.gatehouse.gateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class ProblemDetailUtil {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailUtil.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ProblemDetailUtil() {}

    public static Mono<Void> writeProblemDetail(ServerHttpResponse response, HttpStatus status,
                                               String title, String detail, String instancePath) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (instancePath != null) {
            problem.setInstance(URI.create(instancePath));
        }
        problem.setProperty("timestamp", Instant.now().toString());

        byte[] bytes;
        try {
            bytes = OBJECT_MAPPER.writeValueAsBytes(problem);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"title\":\"" + title + "\",\"detail\":\"" + detail + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
