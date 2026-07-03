package com.campushare.user.controller;

import com.campushare.common.exception.BusinessException;
import com.campushare.common.result.Result;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    @Value("${file.upload-path}")
    private String uploadPath;

    @Value("${file.access-url}")
    private String accessUrl;

    @Value("${file.max-concurrent-uploads:10}")
    private int maxConcurrentUploads;

    @Value("${file.min-free-space-mb:100}")
    private long minFreeSpaceMb;

    @Value("${file.max-file-size-mb:100}")
    private long maxFileSizeMb;

    private final MeterRegistry meterRegistry;

    private Semaphore uploadSemaphore;
    private Path tmpDir;

    private final Map<String, Counter> totalCounters = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> sizeSummaries = new ConcurrentHashMap<>();
    private final Map<String, Timer> uploadTimers = new ConcurrentHashMap<>();
    private Counter failedCounter;
    private Counter rejectedCounter;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @PostConstruct
    public void init() {
        try {
            Path uploadDir = Paths.get(uploadPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                log.info("上传目录已创建: {}", uploadDir.toAbsolutePath());
            } else {
                log.info("上传目录已存在: {}", uploadDir.toAbsolutePath());
            }

            tmpDir = uploadDir.resolve(".tmp");
            if (!Files.exists(tmpDir)) {
                Files.createDirectories(tmpDir);
                log.info("临时文件目录已创建: {}", tmpDir.toAbsolutePath());
            }

            uploadSemaphore = new Semaphore(maxConcurrentUploads, true);
            log.info("文件上传并发限制: {}", maxConcurrentUploads);
            log.info("单个文件大小限制: {}MB", maxFileSizeMb);
            log.info("磁盘最小可用空间: {}MB", minFreeSpaceMb);

            failedCounter = Counter.builder("campushare.file.upload.failed")
                    .register(meterRegistry);
            rejectedCounter = Counter.builder("campushare.file.upload.rejected")
                    .register(meterRegistry);

            cleanupStaleTempFiles();

            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupStaleTempFiles));
        } catch (IOException e) {
            log.error("初始化上传目录失败", e);
            throw new RuntimeException("初始化上传目录失败", e);
        }
    }

    private void cleanupStaleTempFiles() {
        try {
            if (tmpDir != null && Files.exists(tmpDir)) {
                Files.walk(tmpDir)
                        .filter(Files::isRegularFile)
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                                log.debug("清理临时文件: {}", p);
                            } catch (IOException e) {
                                log.warn("清理临时文件失败: {}", p);
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("清理临时文件异常", e);
        }
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            rejectedCounter.increment();
            throw new BusinessException(50001, "文件不能为空");
        }

        long fileSize = file.getSize();
        long maxFileSize = maxFileSizeMb * 1024 * 1024;
        if (fileSize > maxFileSize) {
            rejectedCounter.increment();
            log.warn("文件大小超限: {}MB，限制: {}MB", fileSize / 1024 / 1024, maxFileSizeMb);
            throw new BusinessException(50005, "文件大小不能超过 " + maxFileSizeMb + "MB");
        }

        Path targetDir = Paths.get(uploadPath);
        try {
            long usableSpace = Files.getFileStore(targetDir).getUsableSpace();
            if (usableSpace < minFreeSpaceMb * 1024 * 1024) {
                rejectedCounter.increment();
                log.warn("磁盘空间不足，拒绝上传。可用空间: {}MB，要求最小: {}MB",
                        usableSpace / 1024 / 1024, minFreeSpaceMb);
                throw new BusinessException(50003, "服务器存储空间不足，请稍后再试");
            }
        } catch (IOException e) {
            log.warn("检查磁盘空间失败", e);
        }

        boolean acquired = false;
        Timer.Sample sample = Timer.start(meterRegistry);
        Path tempFile = null;
        String fileType = "unknown";

        try {
            acquired = uploadSemaphore.tryAcquire(5, TimeUnit.SECONDS);
            if (!acquired) {
                rejectedCounter.increment();
                log.warn("上传并发超限，当前限制: {}", maxConcurrentUploads);
                throw new BusinessException(50004, "当前上传人数较多，请稍后再试");
            }

            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            fileType = fileExtension.isEmpty() ? "unknown" : fileExtension.substring(1).toLowerCase();

            String dateStr = LocalDate.now().format(DATE_FORMAT);
            String newFileName = UUID.randomUUID().toString().replace("-", "") + fileExtension;
            String relativePath = dateStr + "/" + newFileName;

            Path dirPath = targetDir.resolve(dateStr);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path targetPath = targetDir.resolve(relativePath);
            tempFile = tmpDir.resolve(UUID.randomUUID().toString().replace("-", "") + ".tmp");

            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            tempFile = null;

            getOrCreateTotalCounter(fileType).increment();
            getOrCreateSizeSummary(fileType).record(fileSize);
            sample.stop(getOrCreateUploadTimer(fileType));

            Map<String, Object> data = new HashMap<>();
            data.put("url", accessUrl + relativePath);
            data.put("fileName", originalFilename);
            data.put("fileType", fileType);
            data.put("fileSize", fileSize);

            log.info("文件上传成功: type={}, size={}KB, name={}", fileType, fileSize / 1024, newFileName);
            return Result.success(data);
        } catch (BusinessException e) {
            sample.stop(getOrCreateUploadTimer(fileType));
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failedCounter.increment();
            throw new BusinessException(50002, "上传被中断");
        } catch (IOException e) {
            log.error("文件上传失败", e);
            failedCounter.increment();
            sample.stop(getOrCreateUploadTimer(fileType));
            throw new BusinessException(50002, "文件上传失败");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
            if (acquired) {
                uploadSemaphore.release();
            }
        }
    }

    private Counter getOrCreateTotalCounter(String fileType) {
        return totalCounters.computeIfAbsent(fileType, type ->
                Counter.builder("campushare.file.upload.total")
                        .tag("type", type)
                        .register(meterRegistry));
    }

    private DistributionSummary getOrCreateSizeSummary(String fileType) {
        return sizeSummaries.computeIfAbsent(fileType, type ->
                DistributionSummary.builder("campushare.file.upload.size")
                        .tag("type", type)
                        .baseUnit("bytes")
                        .register(meterRegistry));
    }

    private Timer getOrCreateUploadTimer(String fileType) {
        return uploadTimers.computeIfAbsent(fileType, type ->
                Timer.builder("campushare.file.upload.duration")
                        .tag("type", type)
                        .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                        .register(meterRegistry));
    }
}
