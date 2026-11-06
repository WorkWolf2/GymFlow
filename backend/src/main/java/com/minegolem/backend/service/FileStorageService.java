package com.minegolem.backend.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${storage.local.dir:uploads}")
    private String localDir;

    public String store(MultipartFile file, String prefix) {
        String ext = getExtension(file.getOriginalFilename());
        String objectName = prefix + "/" + UUID.randomUUID() + ext;

        try {
            ensureBucketExists();

            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

            log.debug("Stored file in MinIO: {}", objectName);
            return objectName;

        } catch (Exception e) {
            log.warn("MinIO upload non disponibile o fallito ({}), fallback su storage disco locale: {}", e.getMessage(), objectName);
            return storeLocally(file, objectName);
        }
    }

    public String storeBytes(byte[] data, String objectName, String contentType) {
        try {
            ensureBucketExists();

            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(new java.io.ByteArrayInputStream(data), data.length, -1)
                .contentType(contentType != null ? contentType : "application/pdf")
                .build());

            log.debug("Stored bytes file in MinIO: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.warn("MinIO storeBytes non disponibile o fallito ({}), fallback su storage disco locale: {}", e.getMessage(), objectName);
            return storeBytesLocally(data, objectName);
        }
    }

    private String storeLocally(MultipartFile file, String objectName) {
        try {
            Path target = Paths.get(localDir).resolve(objectName).normalize();
            Files.createDirectories(target.getParent());
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("File memorizzato localmente con successo: {}", target.toAbsolutePath());
            return "local:" + objectName;
        } catch (Exception ex) {
            log.error("Impossibile salvare il file localmente: {}", objectName, ex);
            throw new RuntimeException("File storage failed on both MinIO and local disk: " + ex.getMessage(), ex);
        }
    }

    private String storeBytesLocally(byte[] data, String objectName) {
        try {
            Path target = Paths.get(localDir).resolve(objectName).normalize();
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            log.info("Bytes memorizzati localmente con successo: {}", target.toAbsolutePath());
            return "local:" + objectName;
        } catch (Exception ex) {
            log.error("Impossibile salvare i bytes localmente: {}", objectName, ex);
            throw new RuntimeException("File storage failed on both MinIO and local disk: " + ex.getMessage(), ex);
        }
    }

    public String getPresignedUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        return "/api/files/download?path=" + URLEncoder.encode(objectName, StandardCharsets.UTF_8);
    }

    public InputStream get(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }

        String cleanPath = objectName.startsWith("local:") ? objectName.substring(6) : objectName;
        Path localFile = Paths.get(localDir).resolve(cleanPath).normalize();
        if (Files.exists(localFile)) {
            try {
                return Files.newInputStream(localFile);
            } catch (Exception e) {
                log.error("Errore lettura file locale: {}", localFile, e);
            }
        }

        try {
            return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(cleanPath)
                .build());
        } catch (Exception e) {
            log.error("Impossibile recuperare il file da MinIO: {}", cleanPath, e);
            throw new RuntimeException("Failed to retrieve file: " + objectName, e);
        }
    }

    public void delete(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
        String cleanPath = objectName.startsWith("local:") ? objectName.substring(6) : objectName;
        Path localFile = Paths.get(localDir).resolve(cleanPath).normalize();
        if (Files.exists(localFile)) {
            try {
                Files.deleteIfExists(localFile);
            } catch (Exception e) {
                log.warn("Errore eliminazione file locale: {}", localFile, e);
            }
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(cleanPath)
                .build());
        } catch (Exception e) {
            log.debug("MinIO delete file ignorato o fallito: {}", cleanPath);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Created MinIO bucket: {}", bucket);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
