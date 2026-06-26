package com.campushare.user.controller;

import com.campushare.common.exception.BusinessException;
import com.campushare.common.result.Result;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    @Value("${file.upload-path}")
    private String uploadPath;

    @Value("${file.access-url}")
    private String accessUrl;

    private final MeterRegistry meterRegistry;

    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(50001, "文件不能为空");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileType = fileExtension.replace(".", "").toLowerCase();

            Counter.builder("campushare.file.upload.total")
                    .tag("type", fileType.isEmpty() ? "unknown" : fileType)
                    .register(meterRegistry)
                    .increment();

            DistributionSummary.builder("campushare.file.upload.size")
                    .tag("type", fileType.isEmpty() ? "unknown" : fileType)
                    .baseUnit("bytes")
                    .register(meterRegistry)
                    .record(file.getSize());

            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String newFileName = UUID.randomUUID().toString().replace("-", "") + fileExtension;
            String relativePath = dateStr + "/" + newFileName;

            Path dirPath = Paths.get(uploadPath + dateStr);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path filePath = Paths.get(uploadPath + relativePath);
            file.transferTo(filePath.toFile());

            Map<String, Object> data = new HashMap<>();
            data.put("url", accessUrl + relativePath);
            data.put("fileName", originalFilename);
            data.put("fileType", fileType);
            data.put("fileSize", file.getSize());

            return Result.success(data);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            Counter.builder("campushare.file.upload.failed")
                    .register(meterRegistry)
                    .increment();
            throw new BusinessException(50002, "文件上传失败");
        }
    }
}
