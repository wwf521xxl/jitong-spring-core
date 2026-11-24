package com.jitong.core.system;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Slf4j
public class FrontendController {

    private static final String FALLBACK = """
            <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
            <title>计件通 Spring Boot 核心模块</title></head>
            <body style="font-family:sans-serif;padding:40px">
            <h1>计件通 · Spring Boot 核心模块</h1>
            <p>前端静态资源未打包。请先执行 <code>mvn package</code>，或直接访问
            <a href="/api/docs">/api/docs</a> 查看接口文档。</p>
            </body></html>
            """;

    @GetMapping({"/", "/index.html"})
    public ResponseEntity<String> index() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/index.html");
        String html;
        if (!resource.exists()) {
            log.warn("static/index.html 不存在，返回占位页");
            html = FALLBACK;
        } else {
            html = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            html = html.replaceFirst("<head>",
                    "<head>\n  <script>window.JITONG_MODE = 'server';</script>");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .body(html);
    }
}
