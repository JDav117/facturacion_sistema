package com.facturacion.infrastructure.adapter.out.backup.couchdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "couchdb")
public record CouchDbProperties(String url, String username, String password, String database, String fallbackDir) {
}
