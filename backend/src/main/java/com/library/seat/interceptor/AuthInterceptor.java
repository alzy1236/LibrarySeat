package com.library.seat.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.seat.common.Constants;
import com.library.seat.common.Result;
import com.library.seat.common.UserContext;
import com.library.seat.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 登录鉴权拦截器：校验 JWT，并将登录用户放入 ThreadLocal
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.jwt.header:Authorization}")
    private String header;

    @Value("${app.jwt.prefix:Bearer }")
    private String prefix;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        return handle(request, response) && checkRole(request, response);
    }

    private boolean handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(header);
        if (token == null || token.isEmpty()) {
            token = request.getParameter("token");
        }
        if (token != null && token.startsWith(prefix)) {
            token = token.substring(prefix.length());
        }
        if (token == null || token.trim().isEmpty()) {
            writeError(response, 401, "未登录或登录已过期，请重新登录");
            return false;
        }
        UserContext.LoginUser user = jwtUtil.parse(token.trim());
        if (user == null) {
            writeError(response, 401, "登录状态已失效，请重新登录");
            return false;
        }
        UserContext.set(user);
        return true;
    }

    /**
     * 管理员接口权限校验：/api/admin/** 仅管理员可访问
     */
    private boolean checkRole(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String uri = request.getRequestURI();
        if (uri.contains("/api/admin/") && !UserContext.isAdmin()) {
            writeError(response, 403, "无权限访问，该功能仅管理员可用");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(Result.error(code, message)));
            writer.flush();
        }
    }
}