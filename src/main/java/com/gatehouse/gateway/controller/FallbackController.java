package com.gatehouse.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

@RestController
public class FallbackController {

    @RequestMapping(value = "/fallback", produces = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
    public Mono<ResponseEntity<ProblemDetail>> fallback(ServerWebExchange exchange) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The requested downstream service is temporarily unavailable or circuit breaker has opened. Please retry later."
        );
        problem.setTitle("Service Unavailable");
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("gateway", "gatehouse-gateway");

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem));
    }
}
