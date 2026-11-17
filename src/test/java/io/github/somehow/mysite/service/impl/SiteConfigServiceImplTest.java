package io.github.somehow.mysite.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.somehow.mysite.config.SiteProperties;
import io.github.somehow.mysite.dao.entity.SiteConfigDO;
import io.github.somehow.mysite.dao.mapper.SiteConfigMapper;
import io.github.somehow.mysite.dto.req.site.SiteConfigUpdateReqDTO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SiteConfigServiceImpl} 语义测试：DB 非空字段覆盖 env 默认，
 * 空白字段归一为 null（清除覆盖值），apply 时跳过 null 保留 env 绑定值。
 */
class SiteConfigServiceImplTest {

    private SiteConfigMapper mapper;
    private SiteProperties props;
    private SiteConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        // LambdaUpdateWrapper 需要实体列缓存；纯单测无 MyBatis 上下文，手动初始化
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""), SiteConfigDO.class);
        mapper = mock(SiteConfigMapper.class);
        props = new SiteProperties();
        props.setTitle("Env Title");
        props.setDescription("Env Desc");
        service = new SiteConfigServiceImpl(mapper, props);
    }

    @Test
    void updateInsertsRowWhenMissingAndAppliesNonBlankFields() {
        when(mapper.selectById(1L)).thenReturn(null);

        SiteConfigUpdateReqDTO dto = new SiteConfigUpdateReqDTO();
        dto.setTitle("DB Title");
        dto.setDescription("   "); // 空白 → 清除覆盖值
        dto.setCommentEnabled(false);

        service.update(dto);

        verify(mapper).insert(argThat((SiteConfigDO row) ->
                row.getId().equals(1L)
                        && "DB Title".equals(row.getTitle())
                        && row.getDescription() == null
                        && Boolean.FALSE.equals(row.getCommentEnabled())));
        // 非空覆盖，空白保留 env
        assertEquals("DB Title", props.getTitle());
        assertEquals("Env Desc", props.getDescription());
        assertFalse(props.isCommentEnabled());
    }

    @Test
    void updateWritesNullsViaWrapperWhenRowExists() {
        SiteConfigDO existing = SiteConfigDO.builder().id(1L).title("Old").build();
        when(mapper.selectById(1L)).thenReturn(existing);

        SiteConfigUpdateReqDTO dto = new SiteConfigUpdateReqDTO();
        dto.setTitle(""); // 清除 title 覆盖 → 回退 env
        dto.setGithub("https://github.com/x");

        service.update(dto);

        // 存在行走 UpdateWrapper 全列 set（含 null），不走 updateById
        verify(mapper).update(isNull(), any());
        verify(mapper, never()).insert(any(SiteConfigDO.class));
        assertEquals("Env Title", props.getTitle());
        assertEquals("https://github.com/x", props.getGithub());
    }
}
