package io.github.somehow.mysite.service.impl;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.config.ImageUploadConfig;
import io.github.somehow.mysite.dao.entity.ImageDO;
import io.github.somehow.mysite.dao.mapper.ImageMapper;
import io.github.somehow.mysite.dto.req.image.ImagePageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.ImagePageQueryRespDTO;
import io.github.somehow.mysite.dto.resp.ImageUploadRespDTO;
import io.github.somehow.mysite.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl extends ServiceImpl<ImageMapper, ImageDO> implements ImageService {

    private final ImageUploadConfig imageUploadConfig;
    private final StringRedisTemplate stringRedisTemplate;

    private static final Map<String, byte[]> MAGIC_NUMBERS = Map.of(
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "image/gif", new byte[]{0x47, 0x49, 0x46, 0x38}
    );

    private static final Map<String, String> EXTENSION_MAP = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp",
            "image/svg+xml", ".svg"
    );

    @Override
    public ImageUploadRespDTO uploadImage(MultipartFile file) {
        checkUploadRateLimit();

        if (file == null || file.isEmpty()) {
            throw new ClientException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        if (file.getSize() > imageUploadConfig.getMaxFileSizeBytes()) {
            throw new ClientException(ErrorCode.IMAGE_FILE_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (StrUtil.isBlank(contentType) || !imageUploadConfig.getAllowedTypes().contains(contentType)) {
            throw new ClientException(ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
        }

        validateMagicNumber(file, contentType);

        String extension = EXTENSION_MAP.getOrDefault(contentType, ".jpg");
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String dayOfMonth = String.valueOf(LocalDate.now().getDayOfMonth());
        String randomHex = HexUtil.encodeHexStr(IdUtil.fastSimpleUUID().substring(0, 8).getBytes());
        String storedName = dayOfMonth + "_" + randomHex + extension;
        String relativePath = datePath + "/" + storedName;

        Path basePath = Paths.get(imageUploadConfig.getBasePath()).toAbsolutePath();
        Path dirPath = basePath.resolve(datePath);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            log.error("创建图片目录失败: {}", dirPath, e);
            throw new ClientException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        Path filePath = dirPath.resolve(storedName);
        try {
            Files.copy(file.getInputStream(), filePath);
        } catch (IOException e) {
            log.error("保存图片文件失败: {}", filePath, e);
            throw new ClientException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        String url = imageUploadConfig.getUrlPath() + relativePath;
        Integer width = null;
        Integer height = null;

        if (!"image/svg+xml".equals(contentType)) {
            try {
                BufferedImage image = ImageIO.read(filePath.toFile());
                if (image != null) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            } catch (IOException e) {
                log.warn("读取图片尺寸失败: {}", filePath, e);
            }
        }

        String userId = UserContext.getUserId();
        Long uploaderId = userId != null ? Long.parseLong(userId) : 0L;

        ImageDO imageDO = ImageDO.builder()
                .id(IdUtil.getSnowflakeNextId())
                .originalName(file.getOriginalFilename())
                .storedName(storedName)
                .filePath(relativePath)
                .url(url)
                .fileSize(file.getSize())
                .contentType(contentType)
                .width(width)
                .height(height)
                .sourceType(0)
                .uploaderId(uploaderId)
                .articleCount(0)
                .build();
        imageDO.setDelFlag(0);

        baseMapper.insert(imageDO);

        return buildUploadResp(imageDO);
    }

    @Override
    public ImageUploadRespDTO uploadImageByUrl(String imageUrl) {
        checkUploadRateLimit();

        if (StrUtil.isBlank(imageUrl)) {
            throw new ClientException(ErrorCode.IMAGE_URL_INVALID);
        }

        URI uri;
        try {
            uri = URI.create(imageUrl);
        } catch (Exception e) {
            throw new ClientException(ErrorCode.IMAGE_URL_INVALID);
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ClientException(ErrorCode.IMAGE_URL_INVALID);
        }

        checkSsrf(uri);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(imageUploadConfig.getUrlFetchConnectTimeout()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest headRequest = HttpRequest.newBuilder()
                .uri(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofMillis(imageUploadConfig.getUrlFetchReadTimeout()))
                .build();

        HttpResponse<Void> headResponse;
        try {
            headResponse = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new ClientException(ErrorCode.IMAGE_URL_UNREACHABLE);
        }

        if (headResponse.statusCode() < 200 || headResponse.statusCode() >= 300) {
            throw new ClientException(ErrorCode.IMAGE_URL_UNREACHABLE);
        }

        String contentType = headResponse.headers().firstValue("Content-Type").orElse("");
        String baseContentType = contentType.split(";")[0].trim();
        if (!imageUploadConfig.getAllowedTypes().contains(baseContentType)) {
            throw new ClientException(ErrorCode.IMAGE_URL_NOT_IMAGE);
        }

        long contentLength = headResponse.headers().firstValue("Content-Length")
                .map(Long::parseLong)
                .orElse(-1L);
        if (contentLength > imageUploadConfig.getMaxFileSizeBytes()) {
            throw new ClientException(ErrorCode.IMAGE_URL_TOO_LARGE);
        }

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .timeout(Duration.ofMillis(imageUploadConfig.getUrlFetchReadTimeout()))
                .build();

        HttpResponse<byte[]> getResponse;
        try {
            getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new ClientException(ErrorCode.IMAGE_URL_FETCH_FAILED);
        }

        if (getResponse.statusCode() < 200 || getResponse.statusCode() >= 300) {
            throw new ClientException(ErrorCode.IMAGE_URL_FETCH_FAILED);
        }

        byte[] imageBytes = getResponse.body();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ClientException(ErrorCode.IMAGE_URL_FETCH_FAILED);
        }

        if (imageBytes.length > imageUploadConfig.getMaxFileSizeBytes()) {
            throw new ClientException(ErrorCode.IMAGE_URL_TOO_LARGE);
        }

        validateMagicNumberFromBytes(imageBytes, baseContentType);

        String extension = EXTENSION_MAP.getOrDefault(baseContentType, ".jpg");
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String dayOfMonth = String.valueOf(LocalDate.now().getDayOfMonth());
        String randomHex = HexUtil.encodeHexStr(IdUtil.fastSimpleUUID().substring(0, 8).getBytes());
        String storedName = dayOfMonth + "_" + randomHex + extension;
        String relativePath = datePath + "/" + storedName;

        Path basePath = Paths.get(imageUploadConfig.getBasePath()).toAbsolutePath();
        Path dirPath = basePath.resolve(datePath);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            log.error("创建图片目录失败: {}", dirPath, e);
            throw new ClientException(ErrorCode.IMAGE_URL_FETCH_FAILED);
        }

        Path filePath = dirPath.resolve(storedName);
        try {
            Files.write(filePath, imageBytes);
        } catch (IOException e) {
            log.error("保存远程图片文件失败: {}", filePath, e);
            throw new ClientException(ErrorCode.IMAGE_URL_FETCH_FAILED);
        }

        String url = imageUploadConfig.getUrlPath() + relativePath;
        Integer width = null;
        Integer height = null;

        if (!"image/svg+xml".equals(baseContentType)) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (image != null) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            } catch (IOException e) {
                log.warn("读取远程图片尺寸失败", e);
            }
        }

        String originalName = extractFileNameFromUrl(imageUrl, extension);
        String userId = UserContext.getUserId();
        Long uploaderId = userId != null ? Long.parseLong(userId) : 0L;

        ImageDO imageDO = ImageDO.builder()
                .id(IdUtil.getSnowflakeNextId())
                .originalName(originalName)
                .storedName(storedName)
                .filePath(relativePath)
                .url(url)
                .fileSize((long) imageBytes.length)
                .contentType(baseContentType)
                .width(width)
                .height(height)
                .sourceType(1)
                .sourceUrl(imageUrl)
                .uploaderId(uploaderId)
                .articleCount(0)
                .build();
        imageDO.setDelFlag(0);

        baseMapper.insert(imageDO);

        return buildUploadResp(imageDO);
    }

    @Override
    public IPage<ImagePageQueryRespDTO> pageQueryImages(ImagePageQueryReqDTO requestParam) {
        return baseMapper.pageQueryImages(requestParam);
    }

    @Override
    public void deleteImage(Long id) {
        LambdaQueryWrapper<ImageDO> queryWrapper = Wrappers.lambdaQuery(ImageDO.class)
                .eq(ImageDO::getId, id)
                .eq(ImageDO::getDelFlag, 0);
        ImageDO imageDO = baseMapper.selectOne(queryWrapper);
        if (imageDO == null) {
            throw new ClientException(ErrorCode.IMAGE_NOT_FOUND);
        }

        checkImageOwnership(imageDO);

        Path filePath = Paths.get(imageUploadConfig.getBasePath()).toAbsolutePath().resolve(imageDO.getFilePath());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("删除图片文件失败: {}", filePath, e);
        }

        ImageDO updateDO = new ImageDO();
        updateDO.setDelFlag(1);
        int rows = baseMapper.update(updateDO, Wrappers.lambdaUpdate(ImageDO.class)
                .eq(ImageDO::getId, id)
                .eq(ImageDO::getDelFlag, 0));
        if (rows <= 0) {
            throw new ClientException(ErrorCode.IMAGE_DELETE_FAILED);
        }
    }

    private void checkUploadRateLimit() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            return;
        }

        String key = "image:upload:limit:" + userId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }
        if (count != null && count > imageUploadConfig.getMaxUploadsPerMinute()) {
            throw new ClientException(ErrorCode.IMAGE_UPLOAD_RATE_LIMITED);
        }
    }

    private void validateMagicNumber(MultipartFile file, String contentType) {
        if ("image/webp".equals(contentType) || "image/svg+xml".equals(contentType)) {
            return;
        }

        byte[] magic = MAGIC_NUMBERS.get(contentType);
        if (magic == null) {
            return;
        }

        try {
            byte[] header = new byte[magic.length];
            try (var is = file.getInputStream()) {
                int read = is.read(header);
                if (read < magic.length) {
                    throw new ClientException(ErrorCode.IMAGE_FILE_INVALID);
                }
            }
            for (int i = 0; i < magic.length; i++) {
                if (header[i] != magic[i]) {
                    throw new ClientException(ErrorCode.IMAGE_FILE_INVALID);
                }
            }
        } catch (ClientException e) {
            throw e;
        } catch (IOException e) {
            log.error("读取图片文件头失败", e);
            throw new ClientException(ErrorCode.IMAGE_FILE_INVALID);
        }
    }

    private void validateMagicNumberFromBytes(byte[] data, String contentType) {
        if ("image/webp".equals(contentType) || "image/svg+xml".equals(contentType)) {
            return;
        }

        byte[] magic = MAGIC_NUMBERS.get(contentType);
        if (magic == null) {
            return;
        }

        if (data.length < magic.length) {
            throw new ClientException(ErrorCode.IMAGE_FILE_INVALID);
        }

        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                throw new ClientException(ErrorCode.IMAGE_FILE_INVALID);
            }
        }
    }

    private void checkSsrf(URI uri) {
        String host = uri.getHost();
        if (StrUtil.isBlank(host)) {
            throw new ClientException(ErrorCode.IMAGE_URL_INVALID);
        }

        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "0.0.0.0".equals(host) || "::1".equals(host)) {
            throw new ClientException(ErrorCode.IMAGE_SSRF_BLOCKED);
        }

        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                throw new ClientException(ErrorCode.IMAGE_SSRF_BLOCKED);
            }
        } catch (ClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientException(ErrorCode.IMAGE_URL_UNREACHABLE);
        }
    }

    private String extractFileNameFromUrl(String url, String defaultExtension) {
        try {
            String path = URI.create(url).getPath();
            if (StrUtil.isNotBlank(path)) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                    String fileName = path.substring(lastSlash + 1);
                    if (StrUtil.isNotBlank(fileName) && fileName.length() < 255) {
                        return fileName;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "remote_image" + defaultExtension;
    }

    private void checkImageOwnership(ImageDO imageDO) {
        if (UserContext.isDeveloper()) {
            return;
        }

        String currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new ClientException(ErrorCode.IMAGE_DELETE_FAILED);
        }

        if (!currentUserId.equals(imageDO.getUploaderId().toString())) {
            throw new ClientException(ErrorCode.IMAGE_DELETE_FAILED);
        }
    }

    private ImageUploadRespDTO buildUploadResp(ImageDO imageDO) {
        return ImageUploadRespDTO.builder()
                .id(imageDO.getId())
                .originalName(imageDO.getOriginalName())
                .url(imageDO.getUrl())
                .fileSize(imageDO.getFileSize())
                .contentType(imageDO.getContentType())
                .width(imageDO.getWidth())
                .height(imageDO.getHeight())
                .build();
    }
}
