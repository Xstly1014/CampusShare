package com.campushare.post.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        createPostDownloadsTable();
    }

    private void createPostDownloadsTable() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS post_downloads (" +
                            "id INT PRIMARY KEY AUTO_INCREMENT, " +
                            "post_id VARCHAR(36) NOT NULL, " +
                            "user_id VARCHAR(36) NOT NULL, " +
                            "download_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "INDEX idx_user_time (user_id, download_time), " +
                            "INDEX idx_post (post_id)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下载历史表'");
            log.info("post_downloads table ensured");
        } catch (Exception e) {
            log.warn("Failed to create post_downloads table: {}", e.getMessage());
        }
    }
}
