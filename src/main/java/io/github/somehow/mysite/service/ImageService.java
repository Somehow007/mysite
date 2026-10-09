package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import io.github.somehow.mysite.dao.entity.ImageDO;
import io.github.somehow.mysite.dto.req.image.ImagePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ImagePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ImageUploadRespDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ImageService extends IService<ImageDO> {

    ImageUploadRespDTO uploadImage(MultipartFile file);

    /**
     * 文档导入批量转存用：跳过单次上传限流（由导入服务控制批量与超时）。
     */
    ImageUploadRespDTO uploadImageForImport(MultipartFile file);

    ImageUploadRespDTO uploadImageByUrl(String url);

    IPage<ImagePageQueryRespDTO> pageQueryImages(ImagePageQueryReqDTO requestParam);

    void deleteImage(Long id);
}
