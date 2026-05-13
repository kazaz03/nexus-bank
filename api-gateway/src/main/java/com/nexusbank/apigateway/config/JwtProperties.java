package com.nexusbank.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds jwt.* properties from application.yml.
 *
 * Only the RSA public key is required here — the gateway verifies tokens
 * but never signs them. The private key lives exclusively in user-service.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String publicKeyPath;

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }
}
