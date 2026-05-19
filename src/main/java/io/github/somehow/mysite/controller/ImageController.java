package io.github.somehow.mysite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.commons.framework.web.Results;
import io.github.somehow.mysite.dto.req.image.ImagePageQueryReqDTO;
import io.github.somehow.mysite.dto.req.image.ImageUrlUploadReqDTO;
import io.github.somehow.mysite.dto.resp.ImagePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ImageUploadRespDTO;
import io.github.somehow.mysite.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Tag(name = "图片管理")
public class ImageController {

    private final ImageService imageService;

    @Operation(summary = "分页查询图片列表")
    @GetMapping("/v1/images")
    public Result<IPage<ImagePageQueryRespDTO>> pageQueryImages(ImagePageQueryReqDTO requestParam) {
        return Results.success(imageService.pageQueryImages(requestParam));
    }

    @Operation(summary = "本地上传图片")
    @PostMapping(value = "/v1/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImageUploadRespDTO> uploadImage(@RequestParam("file") MultipartFile file) {
        return Results.success(imageService.uploadImage(file));
    }

    @Operation(summary = "通过URL上传图片")
    @PostMapping("/v1/images/upload-url")
    public Result<ImageUploadRespDTO> uploadImageByUrl(@RequestBody ImageUrlUploadReqDTO requestParam) {
        return Results.success(imageService.uploadImageByUrl(requestParam.getUrl()));
    }

    @Operation(summary = "删除图片")
    @DeleteMapping("/v1/images/{id}")
    public Result<Void> deleteImage(@PathVariable Long id) {
        imageService.deleteImage(id);
        return Results.success();
    }
}
