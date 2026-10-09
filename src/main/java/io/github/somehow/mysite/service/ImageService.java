package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import io.github.somehow.mysite.dao.entity.ImageDO;
import io.github.somehow.mysite.dto.req.image.ImagePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ImagePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ImageUploadRespDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;

public interface ImageService extends IService<ImageDO> {

    ImageUploadRespDTO uploadImage(MultipartFile file);

    /**
     * 文档导入批量转存用：跳过单次上传限流（由导入服务控制批量与超时）。
     */
    ImageUploadRespDTO uploadImageForImport(MultipartFile file);

    ImageUploadRespDTO uploadImageByUrl(String url);

    /**
     * 将正文/封面中的暂存图片转正（status 0→1）并累计引用数。
     * 幂等：已是正式状态的不重复计数。
     *
     * @return 本次转正张数
     */
    int promoteStagingImages(Collection<String> imageUrls);

    /**
     * 清理超过保留期且未被任何文章引用的暂存图片（删文件 + 逻辑删记录）。
     *
     * @return 清理张数
     */
    int cleanupExpiredStagingImages();

    IPage<ImagePageQueryRespDTO> pageQueryImages(ImagePageQueryReqDTO requestParam);

    void deleteImage(Long id);
}
