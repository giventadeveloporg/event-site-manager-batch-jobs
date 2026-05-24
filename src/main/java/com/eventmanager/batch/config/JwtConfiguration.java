package com.eventmanager.batch.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Optional JWT encoder/decoder beans. Only loaded when {@code jhipster.security.authentication.jwt.base64-secret}
 * is configured. Batch job trigger endpoints do not require JWT ({@link SecurityConfiguration}).
 */
@Configuration
@ConditionalOnExpression(
    "T(org.springframework.util.StringUtils).hasText(@environment.getProperty('jhipster.security.authentication.jwt.base64-secret'))"
)
public class JwtConfiguration {

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String base64Secret;

    @Bean
    public JwtEncoder jwtEncoder() {
        byte[] secretBytes = Base64.getDecoder().decode(base64Secret);
        SecretKey secretKey = new SecretKeySpec(secretBytes, 0, secretBytes.length, "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] secretBytes = Base64.getDecoder().decode(base64Secret);
        SecretKey secretKey = new SecretKeySpec(secretBytes, 0, secretBytes.length, "HmacSHA256");
        return NimbusJwtDecoder
            .withSecretKey(secretKey)
            .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
            .build();
    }
}
