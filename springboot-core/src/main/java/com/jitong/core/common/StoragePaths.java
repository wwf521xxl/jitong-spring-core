package com.jitong.core.common;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@Getter
public class StoragePaths {

    private final Path dataDir;
    private final Path photosDir;
    private final Path backupsDir;

    public StoragePaths(com.jitong.core.config.JitongProperties properties) {
        this.dataDir = Path.of(properties.getDataDir()).toAbsolutePath().normalize();
        this.photosDir = dataDir.resolve("photos");
        this.backupsDir = dataDir.resolve("backups");
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(photosDir);
        Files.createDirectories(backupsDir);
    }
}
