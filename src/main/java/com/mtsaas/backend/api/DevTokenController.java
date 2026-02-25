package com.mtsaas.backend.api;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Key;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dev")
public class DevTokenController {

    @GetMapping("/expired-token")
    public Map<String, String> expiredToken() {
        String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        Key key = Keys.hmacShaKeyFor(keyBytes);

        long now = System.currentTimeMillis();
        Date iat = new Date(now - 2L * 24 * 60 * 60 * 1000);
        Date exp = new Date(now - 1L * 24 * 60 * 60 * 1000);

        String token = Jwts.builder()
                .setSubject("mock@example.com")
                .setIssuedAt(iat)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        return Map.of("token", token);
    }
}
