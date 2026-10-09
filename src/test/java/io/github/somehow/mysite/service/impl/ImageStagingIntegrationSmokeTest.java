package io.github.somehow.mysite.service.impl;

import io.github.somehow.mysite.dao.entity.ImageDO;
import io.github.somehow.mysite.service.ImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实数据源冒烟：暂存孤儿图清理 + 保存转正（需本地 MySQL，与 dev 同库）。
 */
@SpringBootTest
class ImageStagingIntegrationSmokeTest {

    @Autowired
    ImageService imageService;

    @Test
    void promoteAndCleanup_onRealDb() {
        // 1) 构造一张「已过期、未被引用」的暂存图
        ImageDO orphan = ImageDO.builder()
                .id(System.nanoTime())
                .originalName("orphan.png")
                .storedName("orphan.png")
                .filePath("2026/10/orphan-test.png")
                .url("/uploads/images/2026/10/orphan-test-" + System.nanoTime() + ".png")
                .fileSize(1L)
                .contentType("image/png")
                .sourceType(0)
                .uploaderId(1L)
                .articleCount(0)
                .status(ImageDO.STATUS_STAGING)
                .build();
        orphan.setDelFlag(0);
        orphan.setCreateTime(new Date(System.currentTimeMillis() - 49 * 3600 * 1000L));
        orphan.setUpdateTime(orphan.getCreateTime());
        imageService.save(orphan);

        // 2) 构造一张暂存图，用于验证 promote
        ImageDO pending = ImageDO.builder()
                .id(System.nanoTime())
                .originalName("pending.png")
                .storedName("pending.png")
                .filePath("2026/10/pending-test.png")
                .url("/uploads/images/2026/10/pending-test-" + System.nanoTime() + ".png")
                .fileSize(1L)
                .contentType("image/png")
                .sourceType(0)
                .uploaderId(1L)
                .articleCount(0)
                .status(ImageDO.STATUS_STAGING)
                .build();
        pending.setDelFlag(0);
        pending.setCreateTime(new Date());
        pending.setUpdateTime(new Date());
        imageService.save(pending);

        int promoted = imageService.promoteStagingImages(List.of(pending.getUrl()));
        assertEquals(1, promoted);

        ImageDO after = imageService.getById(pending.getId());
        assertNotNull(after);
        assertEquals(ImageDO.STATUS_ACTIVE, after.getStatus());
        assertEquals(1, after.getArticleCount());

        // 3) 清理应删掉过期未引用的 orphan（pending 已转正，不动）
        int cleaned = imageService.cleanupExpiredStagingImages();
        assertTrue(cleaned >= 1, "应至少清理 1 张过期暂存图，实际=" + cleaned);

        ImageDO orphanAfter = imageService.getById(orphan.getId());
        // 逻辑删除后 getById 可能仍返回 del_flag=1 的行，也可能查不到
        if (orphanAfter != null) {
            assertEquals(1, orphanAfter.getDelFlag());
        }

        // 收尾：删掉测试留下的 pending 正式图，避免污染
        imageService.removeById(pending.getId());
        imageService.removeById(orphan.getId());
    }
}
