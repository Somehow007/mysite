package io.github.somehow.mysite.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.dao.entity.ImageDO;
import io.github.somehow.mysite.dto.req.image.ImagePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ImagePageQueryRespDTO;

public interface ImageMapper extends BaseMapper<ImageDO> {

    IPage<ImagePageQueryRespDTO> pageQueryImages(ImagePageQueryReqDTO requestParam);
}
