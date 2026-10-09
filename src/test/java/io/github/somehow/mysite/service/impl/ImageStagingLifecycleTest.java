package io.github.somehow.mysite.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.somehow.mysite.config.ImageUploadConfig;
import io.github.somehow.mysite.dao.entity.ImageDO;
import io.github.somehow.mysite.dao.mapper.ImageMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageStagingLifecycleTest {

    @Mock
    ImageMapper imageMapper;

    @Mock
    ImageUploadConfig imageUploadConfig;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    ImageServiceImpl imageService;

    @BeforeEach
    void bindMapper() throws Exception {
        // Lambda 需要实体表元数据缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ImageDO.class);
        ReflectionTestUtils.setField(imageService, "baseMapper", imageMapper);
        Path tmp = Files.createTempDirectory("img-test");
        lenient().when(imageUploadConfig.getBasePath()).thenReturn(tmp.toString());
    }

    private static ImageDO stagingImage(long id, String url) {
        ImageDO image = ImageDO.builder()
                .id(id)
                .url(url)
                .filePath("2026/10/1_x.png")
                .articleCount(0)
                .status(ImageDO.STATUS_STAGING)
                .build();
        image.setDelFlag(0);
        image.setCreateTime(new Date());
        return image;
    }

    @Test
    void promote_matchesNormalizedUrls_onlyStaging() {
        ImageDO img = stagingImage(1L, "/uploads/images/2026/10/1_a.png");
        when(imageMapper.selectList(any())).thenReturn(List.of(img));
        when(imageMapper.update(isNull(), any())).thenReturn(1);

        int n = imageService.promoteStagingImages(List.of(
                "http://localhost:8081/uploads/images/2026/10/1_a.png?x=1",
                "/uploads/images/2026/10/1_a.png",
                "https://cdn.example.com/other.png"));

        assertEquals(1, n);
        verify(imageMapper).update(isNull(), any());
    }

    @Test
    void promote_emptyOrNull_returnsZero() {
        assertEquals(0, imageService.promoteStagingImages(null));
        assertEquals(0, imageService.promoteStagingImages(List.of()));
        assertEquals(0, imageService.promoteStagingImages(List.of("https://x.com/a.png")));
        verify(imageMapper, never()).selectList(any());
    }

    @Test
    void cleanup_deletesExpiredUnboundStaging() {
        ImageDO img = stagingImage(9L, "/uploads/images/2026/10/9_z.png");
        when(imageMapper.selectList(any())).thenReturn(List.of(img));
        when(imageMapper.update(isNull(), any())).thenReturn(1);

        int n = imageService.cleanupExpiredStagingImages();

        assertEquals(1, n);
        verify(imageMapper, atLeastOnce()).update(any(), any());
    }

    @Test
    void cleanup_nothingExpired_returnsZero() {
        when(imageMapper.selectList(any())).thenReturn(List.of());
        assertEquals(0, imageService.cleanupExpiredStagingImages());
        verify(imageMapper, never()).update(any(), any());
    }
}
