package com.notenest.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Satu-satunya tempat token diverifikasi. Service di belakang tidak pernah
// melihat JWT - mereka menerima identitas yang sudah jadi lewat header X-User-*.
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    // Harus SAMA dengan jwt.secret milik auth-service (yang menandatangani token)
    @Value("${jwt.secret}")
    private String secretKey;

    // Path yang boleh diakses TANPA token
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/docs/specs/"
    );

    // Path yang HANYA boleh dipanggil antar-service (Feign lewat Eureka, langsung
    // ke service, tidak lewat gateway). Token yang sah pun tidak cukup: endpoint
    // internal tidak punya konsep pemilik, jadi siapa pun yang bisa menjangkaunya
    // bisa membaca data user mana pun.
    private static final List<String> INTERNAL_PATTERNS = List.of(
            "/internal/**"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 0. Path internal -> tolak dari luar, sebelum token bahkan diperiksa
        if (INTERNAL_PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
            return deny(exchange, HttpStatus.FORBIDDEN, "Internal endpoint - not reachable through the gateway");
        }

        // 1. Path publik -> langsung teruskan (tapi buang header X-User-* palsu dari luar)
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(stripUserHeaders(exchange));
        }

        // 2. Wajib ada "Authorization: Bearer <token>"
        // scheme case-insensitive sesuai RFC 7235 (Bearer/bearer/BEARER sama saja)
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            return deny(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        // 3. Verifikasi signature + expiry token
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(authHeader.substring(7))
                    .getPayload();

            // 4. Token valid -> teruskan + selipkan identitas user untuk service belakang.
            // X-User-Name ikut dikirim supaya user-service bisa membuat profil dari
            // klaim token, tanpa perlu memanggil balik auth-service.
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.set("X-User-Id", String.valueOf(claims.get("userId")));
                        headers.set("X-User-Email", claims.getSubject());
                        headers.set("X-User-Role", String.valueOf(claims.get("role")));
                        headers.set("X-User-Name", String.valueOf(claims.get("name")));
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (JwtException e) {
            return deny(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    // Buang header identitas yang dikirim client sendiri (anti-spoofing)
    private ServerWebExchange stripUserHeaders(ServerWebExchange exchange) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Role");
                    headers.remove("X-User-Name");
                })
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private Mono<Void> deny(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // -1 = jalankan filter ini sebelum filter routing bawaan gateway
    @Override
    public int getOrder() {
        return -1;
    }
}
