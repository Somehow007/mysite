package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.github.somehow.mysite.dao.entity.ImageDO;
import io.github.somehow.mysite.dto.resp.ImageUploadRespDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ImageService extends IService<ImageDO> {

    ImageUploadRespDTO uploadImage(MultipartFile file);

    ImageUploadRespDTO uploadImageByUrl(String url);

    void deleteImage(Long id);
}
