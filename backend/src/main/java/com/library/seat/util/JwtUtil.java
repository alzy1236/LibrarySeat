package com.library.seat.util;

import com.library.seat.common.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

/**
 * JWT 工具类（JJWT 0.11.x，HS256）
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expire-hours:12}")
    private long expireHours;

    private SecretKey key;

    private SecretKey getKey() {
        if (key == null) {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                // HS256 要求密钥不少于 256 位，不足时右侧补足
                byte[] padded = new byte[32];
                System.arraycopy(bytes, 0, padded, 0, bytes.length);
                Arrays.fill(padded, bytes.length, 32, (byte) '0');
                bytes = padded;
            }
            key = Keys.hmacShaKeyFor(bytes);
        }
        return key;
    }

    public String createToken(Long userId, String username, String role, String realName) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 60 * 60 * 1000);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("realName", realName)
                .setIssuedAt(now)
                .setExpiration(expire)
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 token，失败（过期 / 被篡改）返回 null
     */
    public UserContext.LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            UserContext.LoginUser user = new UserContext.LoginUser();
            user.setUserId(Long.valueOf(claims.getSubject()));
            user.setUsername((String) claims.get("username"));
            user.setRole((String) claims.get("role"));
            user.setRealName((String) claims.get("realName"));
            return user;
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public long getExpireHours() {
        return expireHours;
    }
}