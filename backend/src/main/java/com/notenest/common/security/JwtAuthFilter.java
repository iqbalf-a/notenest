package com.notenest.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Pindahan JwtAuthFilter milik api-gateway ke dunia servlet.
//
// Kenapa ditulis ulang, bukan disalin: Spring Cloud Gateway itu reaktif
// (ServerWebExchange, Mono, GlobalFilter), sedangkan auth/user/note itu Spring MVC.
// Dua stack itu tidak bisa hidup dalam satu aplikasi. Yang dipertahankan persis
// sama adalah KONTRAKNYA - controller di belakang tetap hanya melihat header
// X-User-*, tidak pernah melihat JWT, jadi kodenya tidak perlu diubah sedikit pun.
//
// Bukan @Component dan bukan @Bean dengan sengaja: bean bertipe Filter akan
// didaftarkan dua kali oleh Spring Boot (sekali oleh servlet container, sekali
// oleh SecurityFilterChain). Cukup di-new di SecurityConfig.
public class JwtAuthFilter extends OncePerRequestFilter {

    // Harus SAMA dengan jwt.secret yang dipakai JwtService saat menandatangani token
    private final String secretKey;

    public JwtAuthFilter(String secretKey) {
        this.secretKey = secretKey;
    }

    // Path yang boleh diakses TANPA token
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/docs.html",
            "/docs/specs",
            "/v3/api-docs",
            "/actuator/health",
            "/actuator/info"
    );

    // Sisa dari era microservices: endpoint yang dulu hanya boleh dipanggil Feign.
    // Di build gabungan InternalUserController memang tidak ikut dikompilasi, jadi
    // aturan ini tidak pernah kena. Dibiarkan sebagai jaring pengaman kalau suatu
    // saat ada controller /internal/** yang ikut masuk lagi.
    private static final List<String> INTERNAL_PATTERNS = List.of("/internal/**");

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final Set<String> IDENTITY_HEADERS = Set.of(
            "x-user-id", "x-user-email", "x-user-role", "x-user-name");

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 0. Path internal -> tolak dari luar, sebelum token bahkan diperiksa
        if (INTERNAL_PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
            deny(response, HttpStatus.FORBIDDEN, "Internal endpoint - not reachable from outside");
            return;
        }

        // 1. Preflight CORS tidak membawa Authorization - jangan diminta token.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(new IdentityRequest(request, Map.of()), response);
            return;
        }

        // 2. Path publik -> teruskan, tapi buang header X-User-* palsu dari luar
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(new IdentityRequest(request, Map.of()), response);
            return;
        }

        // 3. Wajib ada Authorization: Bearer <token>.
        // Scheme case-insensitive sesuai RFC 7235 (Bearer/bearer/BEARER sama saja).
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            deny(response, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        // 4. Verifikasi signature + expiry token
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(authHeader.substring(7))
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            deny(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        // 5. Token valid -> selipkan identitas untuk controller di belakang.
        // Persis header yang dulu dikirim gateway, jadi UserController dan
        // NoteController tetap membaca @RequestHeader("X-User-Id") seperti biasa.
        Map<String, String> identity = new LinkedHashMap<>();
        identity.put("X-User-Id", String.valueOf(claims.get("userId")));
        identity.put("X-User-Email", claims.getSubject());
        identity.put("X-User-Role", String.valueOf(claims.get("role")));
        identity.put("X-User-Name", String.valueOf(claims.get("name")));

        // 6. Isi SecurityContext supaya aturan .authenticated() di SecurityConfig lolos.
        // Sengaja TIDAK query database: klaim token sudah cukup, dan gateway dulu
        // juga tidak pernah menyentuh DB untuk ini.
        String role = String.valueOf(claims.get("role"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(new IdentityRequest(request, identity), response);
    }

    private void deny(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Servlet tidak mengizinkan header request diubah, jadi request dibungkus.
    // Dua tugas sekaligus, seperti gateway dulu:
    //   - header X-User-* kiriman client SELALU dibuang (anti-spoofing)
    //   - kalau token sah, X-User-* versi server yang dipasang
    private static final class IdentityRequest extends HttpServletRequestWrapper {

        private final Map<String, String> identity;

        private IdentityRequest(HttpServletRequest request, Map<String, String> identity) {
            super(request);
            this.identity = identity;
        }

        @Override
        public String getHeader(String name) {
            if (isIdentityHeader(name)) {
                return lookup(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isIdentityHeader(name)) {
                String value = lookup(name);
                return value == null
                        ? Collections.emptyEnumeration()
                        : Collections.enumeration(List.of(value));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                if (!isIdentityHeader(name)) {
                    names.add(name);
                }
            }
            names.addAll(identity.keySet());
            return Collections.enumeration(names);
        }

        private String lookup(String name) {
            return identity.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        private boolean isIdentityHeader(String name) {
            return name != null && IDENTITY_HEADERS.contains(name.toLowerCase(Locale.ROOT));
        }
    }
}
