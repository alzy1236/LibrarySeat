package com.library.seat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 首页注入“启动标识”过滤器
 *
 * <p>每次后端启动都会生成一个新的启动时间戳，写入 index.html 的
 * <code>&lt;meta name="ls-boot" content="..."&gt;</code>。前端据此判断“项目是否重启过”：</p>
 * <ul>
 *     <li>项目重启后第一次打开系统 -> 回到登录页，需要重新登录；</li>
 *     <li>普通刷新页面（F5）-> 启动标识不变，保持当前页面与登录状态。</li>
 * </ul>
 */
@Slf4j
@Component
public class BootStampFilter implements Filter {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 本次后端启动的唯一标识 */
    private final String bootStamp = LocalDateTime.now().format(FMT) + "-" + Long.toString(System.nanoTime(), 36);

    private final AtomicReference<String> cachedIndex = new AtomicReference<>(null);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String uri = req.getRequestURI();

        boolean pageRequest = "GET".equalsIgnoreCase(req.getMethod())
                && (uri.equals("/") || uri.equals("/index.html") || uri.equals("/login")
                || uri.startsWith("/student/") || uri.startsWith("/admin/"));

        if (!pageRequest) {
            chain.doFilter(request, response);
            return;
        }

        String html = cachedIndex.get();
        if (html == null) {
            html = loadIndex();
            if (html != null) {
                html = injectStamp(html);
                cachedIndex.set(html);
            }
        }
        if (html == null) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletResponse resp = (HttpServletResponse) response;
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("text/html;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        try (PrintWriter writer = resp.getWriter()) {
            writer.write(html);
            writer.flush();
        }
    }

    /**
     * 把启动标识写入 index.html 的 meta 标签
     */
    private String injectStamp(String html) {
        String tag = "<meta name=\"ls-boot\" content=\"" + bootStamp + "\">";
        String marker = "<meta name=\"ls-boot\" content=\"\">";
        if (html.contains(marker)) {
            return html.replace(marker, tag);
        }
        int idx = html.indexOf("</head>");
        if (idx < 0) {
            return html;
        }
        return html.substring(0, idx) + "    " + tag + "\n  " + html.substring(idx);
    }

    private String loadIndex() {
        try (java.io.InputStream in = new org.springframework.core.io.ClassPathResource("static/index.html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读取 index.html 失败: {}", e.getMessage());
            return null;
        }
    }

    public String getBootStamp() {
        return bootStamp;
    }
}