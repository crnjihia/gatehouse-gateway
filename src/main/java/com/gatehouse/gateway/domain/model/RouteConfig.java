package com.gatehouse.gateway.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("route_configs")
public class RouteConfig {

    @Id
    private String id;

    @Column("route_id")
    private String routeId;

    @Column("uri")
    private String uri;

    @Column("predicates")
    private String predicates;

    @Column("filters")
    private String filters;

    @Column("route_order")
    private Integer routeOrder;

    @Column("enabled")
    private Boolean enabled;

    @Column("description")
    private String description;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    public RouteConfig() {}

    public RouteConfig(String id, String routeId, String uri, String predicates, String filters,
                       Integer routeOrder, Boolean enabled, String description,
                       Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.routeId = routeId;
        this.uri = uri;
        this.predicates = predicates;
        this.filters = filters;
        this.routeOrder = routeOrder;
        this.enabled = enabled;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String routeId;
        private String uri;
        private String predicates;
        private String filters;
        private Integer routeOrder;
        private Boolean enabled;
        private String description;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder routeId(String routeId) { this.routeId = routeId; return this; }
        public Builder uri(String uri) { this.uri = uri; return this; }
        public Builder predicates(String predicates) { this.predicates = predicates; return this; }
        public Builder filters(String filters) { this.filters = filters; return this; }
        public Builder routeOrder(Integer routeOrder) { this.routeOrder = routeOrder; return this; }
        public Builder enabled(Boolean enabled) { this.enabled = enabled; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public RouteConfig build() {
            return new RouteConfig(id, routeId, uri, predicates, filters, routeOrder, enabled, description, createdAt, updatedAt);
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
    public String getPredicates() { return predicates; }
    public void setPredicates(String predicates) { this.predicates = predicates; }
    public String getFilters() { return filters; }
    public void setFilters(String filters) { this.filters = filters; }
    public Integer getRouteOrder() { return routeOrder; }
    public void setRouteOrder(Integer routeOrder) { this.routeOrder = routeOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
