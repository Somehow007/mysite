package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dao.entity.ImageDO;
import io.github.somehow.mysite.dto.req.image.ImagePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ImagePageQueryRespDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ImageMapper extends BaseMapper<ImageDO> {

    IPage<ImagePageQueryRespDTO> pageQueryImages(ImagePageQueryReqDTO requestParam);

    /**
     * 查询未被任何文章引用的图片 ID。
     *
     * @param excludeFreshStaging true 时排除 48h 内新建的暂存图（保护正在编辑的草稿）
     */
    List<Long> selectOrphanImageIds(@Param("excludeFreshStaging") boolean excludeFreshStaging);

    /**
     * 查询超过保留期、且未被任何内容（含草稿文章）引用的暂存图 ID。
     * 不用 article_count：保存草稿转正失败时正文仍可能引用，不能按计数误删。
     */
    List<Long> selectExpiredStagingImageIds();
}
