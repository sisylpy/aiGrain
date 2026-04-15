package com.nongxinle;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class StartupLogRunner implements CommandLineRunner {

    private static final String LOG_DIR = "logs";
    private static final String LOG_PREFIX = "startup_";
    private static final String LOG_EXT = ".log";

    @Override
    public void run(String... args) throws Exception {
        File dir = new File(LOG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        int nextSeq = getNextSequence(dir);
        String fileName = LOG_PREFIX + String.format("%03d", nextSeq) + LOG_EXT;
        File logFile = new File(dir, fileName);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String content = "启动序号: " + nextSeq + "\n启动时间: " + timestamp + "\n";

        try (FileWriter writer = new FileWriter(logFile)) {
            writer.write(content);
        }

        System.out.println("[StartupLog] 已创建日志: " + fileName);
    }

    private int getNextSequence(File dir) {
        File[] files = dir.listFiles((d, name) -> name.startsWith(LOG_PREFIX) && name.endsWith(LOG_EXT));
        if (files == null || files.length == 0) {
            return 1;
        }

        int maxSeq = 0;
        for (File file : files) {
            String name = file.getName();
            String numStr = name.substring(LOG_PREFIX.length(), name.length() - LOG_EXT.length());
            try {
                int seq = Integer.parseInt(numStr);
                if (seq > maxSeq) {
                    maxSeq = seq;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return maxSeq + 1;
    }
}
