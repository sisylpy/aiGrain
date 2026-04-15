package com.nongxinle.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 测试 Controller
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 打印日志接口 - 用于测试热部署
     */
    @GetMapping("/log")
    public String printLog() {
        String now = LocalDateTime.now().format(formatter);
        String message = "[" + now + "] 热部署测试日志 - 修改这段文字，然后刷新页面看是否生效！秀谷1113333";

        
        // 打印各级别日志
        log.trace("TRACE - {}", message,message);
        log.debug("DEBUG - {}", message);
        log.info("INFO  - {}", message);
        log.warn("WARN  - {}", message);
        log.error("ERROR - {}", message);
        
        return message;
    }

}
