package com.minegolem.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseBackupService {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DataSourceProperties dataSourceProperties;

    @Value("${backup.database.enabled:true}")
    private boolean enabled;

    @Value("${backup.database.directory:database-backups}")
    private String backupDirectory;

    @Value("${backup.database.max-files:10}")
    private int maxFiles;

    @Value("${backup.database.pg-dump-command:pg_dump}")
    private String pgDumpCommand;

    @Scheduled(cron = "${backup.database.cron:0 0 3 * * *}")
    public void backupDaily() {
        if (!enabled) {
            return;
        }

        try {
            createBackup();
            enforceRetention();
        } catch (Exception e) {
            log.error("Database backup failed", e);
        }
    }

    public Path createBackup() throws IOException, InterruptedException {
        JdbcPostgresUrl url = JdbcPostgresUrl.parse(dataSourceProperties.getUrl());
        Path directory = Path.of(backupDirectory).toAbsolutePath().normalize();
        Files.createDirectories(directory);

        String fileName = "database-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".db";
        Path target = directory.resolve(fileName);
        Path temp = directory.resolve(fileName + ".tmp");

        ProcessBuilder builder = new ProcessBuilder(
            pgDumpCommand,
            "--host", url.host(),
            "--port", String.valueOf(url.port()),
            "--username", dataSourceProperties.getUsername(),
            "--dbname", url.database(),
            "--format", "custom",
            "--file", temp.toString()
        );
        builder.environment().put("PGPASSWORD", dataSourceProperties.getPassword());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            Files.deleteIfExists(temp);
            throw new IOException("pg_dump exited with code " + exitCode + ": " + output);
        }

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Database backup created: {}", target);
        return target;
    }

    public Map<String, Object> getBackupStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("directory", backupDirectory);
        status.put("maxFiles", maxFiles);

        Path directory = Path.of(backupDirectory).toAbsolutePath().normalize();
        try {
            if (Files.isDirectory(directory)) {
                try (var stream = Files.list(directory)) {
                    List<Map<String, Object>> backups = stream
                            .filter(path -> path.getFileName().toString().endsWith(".db"))
                            .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                            .map(path -> {
                                Map<String, Object> info = new LinkedHashMap<>();
                                info.put("name", path.getFileName().toString());
                                try {
                                    info.put("size", Files.size(path));
                                    info.put("lastModified", Files.getLastModifiedTime(path).toMillis());
                                } catch (IOException e) {
                                    info.put("size", 0);
                                }
                                return info;
                            })
                            .collect(Collectors.toList());
                    status.put("backups", backups);
                    status.put("count", backups.size());
                }
            } else {
                status.put("backups", List.of());
                status.put("count", 0);
            }
        } catch (IOException e) {
            log.error("Failed to read backup status", e);
            status.put("backups", List.of());
            status.put("count", 0);
        }

        return status;
    }

    private void enforceRetention() throws IOException {
        Path directory = Path.of(backupDirectory).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            return;
        }

        List<Path> backups;
        try (var stream = Files.list(directory)) {
            backups = stream
                .filter(path -> path.getFileName().toString().endsWith(".db"))
                .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                .toList();
        }

        for (int i = Math.max(maxFiles, 0); i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
            log.info("Old database backup removed: {}", backups.get(i));
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private record JdbcPostgresUrl(String host, int port, String database) {
        private static JdbcPostgresUrl parse(String jdbcUrl) {
            if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException("Database backup supports PostgreSQL JDBC URLs only");
            }

            String withoutPrefix = jdbcUrl.substring("jdbc:postgresql://".length());
            String withoutParams = withoutPrefix.split("\\?", 2)[0];
            int slash = withoutParams.indexOf('/');
            if (slash < 0) {
                throw new IllegalArgumentException("Invalid PostgreSQL JDBC URL: missing database name");
            }

            String hostPort = withoutParams.substring(0, slash);
            String database = withoutParams.substring(slash + 1);
            int colon = hostPort.lastIndexOf(':');
            String host = colon >= 0 ? hostPort.substring(0, colon) : hostPort;
            int port = colon >= 0 ? Integer.parseInt(hostPort.substring(colon + 1)) : 5432;

            return new JdbcPostgresUrl(host, port, database);
        }
    }
}
