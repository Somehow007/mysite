package io.github.somehow.mysite.service.impl;

import cn.hutool.core.util.StrUtil;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dto.resp.ArticleImportImageDTO;
import io.github.somehow.mysite.dto.resp.ArticleImportRespDTO;
import io.github.somehow.mysite.dto.resp.ImageUploadRespDTO;
import io.github.somehow.mysite.service.ArticleImportService;
import io.github.somehow.mysite.service.ImageService;
import io.github.somehow.mysite.utils.ArticleDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文档导入实现。
 *
 * 稳定性约定（「进程不能卡住」）：
 * <ul>
 *   <li>单张图片 try/catch，失败只记明细 + 写占位，绝不中断整体导入</li>
 *   <li>附件图片（用户一并选中的本地文件）优先按文件名匹配并转存</li>
 *   <li>远程 / data URI 自动转存上限 {@link #MAX_AUTO_IMAGES} 张</li>
 *   <li>整段图片处理硬超时 {@link #IMAGE_PHASE_BUDGET_MS}，超时后剩余全部占位</li>
 *   <li>远程图单张超时 {@link #REMOTE_IMAGE_TIMEOUT_MS}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleImportServiceImpl implements ArticleImportService {

    /** 文档最大 2MB（纯文本） */
    static final long MAX_DOC_BYTES = 2L * 1024 * 1024;

    /** 附件图片单张最大 8MB（略高于图床 5MB，校验交给 ImageService） */
    static final long MAX_ATTACH_BYTES = 8L * 1024 * 1024;

    /** 附件图片张数上限 */
    static final int MAX_ATTACHMENTS = 20;

    /** 远程 / data URI 自动转存张数上限 */
    static final int MAX_AUTO_IMAGES = 8;

    /** 图片阶段总预算 */
    static final long IMAGE_PHASE_BUDGET_MS = 25_000L;

    /** 单张远程图片拉取超时 */
    static final long REMOTE_IMAGE_TIMEOUT_MS = 6_000L;

    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_KEPT = "KEPT";
    private static final String STATUS_FAILED = "FAILED";

    private final ImageService imageService;

    private final ExecutorService remoteFetchExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "article-import-image");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean executorShutdown = new AtomicBoolean(false);

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        if (executorShutdown.compareAndSet(false, true)) {
            remoteFetchExecutor.shutdownNow();
        }
    }

    @Override
    public ArticleImportRespDTO importDocument(MultipartFile file, List<MultipartFile> attachments) {
        validateFile(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取导入文档失败: {}", file.getOriginalFilename(), e);
            throw new ClientException(ErrorCode.ARTICLE_IMPORT_READ_FAILED);
        }

        String rawText;
        try {
            rawText = ArticleDocumentParser.decodeText(bytes);
        } catch (IllegalArgumentException e) {
            throw new ClientException(ErrorCode.ARTICLE_IMPORT_ENCODING_UNSUPPORTED);
        }

        if (StrUtil.isBlank(rawText)) {
            throw new ClientException(ErrorCode.ARTICLE_IMPORT_EMPTY);
        }

        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "untitled.md");
        ArticleDocumentParser.TitleResult titled = ArticleDocumentParser.extractTitle(rawText, fileName);
        String content = titled.content();

        // 附件：basename(小写) → 已上传 URL（先全部转存，再与文中引用匹配）
        Map<String, String> companionUrls = uploadAttachments(attachments);

        List<ArticleImportImageDTO> imageResults = new ArrayList<>();
        Map<String, String> replacements = new HashMap<>();

        if (StrUtil.isNotBlank(content)) {
            processImages(content, replacements, imageResults, companionUrls);
            content = ArticleDocumentParser.replaceImages(content, replacements);
        }

        return ArticleImportRespDTO.builder()
                .title(StrUtil.isBlank(titled.title()) ? null : titled.title())
                .content(content)
                .fileName(fileName)
                .images(imageResults)
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientException(ErrorCode.ARTICLE_IMPORT_FILE_REQUIRED);
        }
        if (file.getSize() > MAX_DOC_BYTES) {
            throw new ClientException(ErrorCode.ARTICLE_IMPORT_FILE_TOO_LARGE);
        }
        String name = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        boolean byName = name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".markdown");
        if (!byName) {
            throw new ClientException(ErrorCode.ARTICLE_IMPORT_TYPE_NOT_ALLOWED);
        }
    }

    /**
     * 转存用户一并选中的图片附件。
     * key = 小写文件名（含扩展名），value = 图床 URL。
     * 单张失败不中断，跳过即可（文中会写补传占位）。
     */
    private Map<String, String> uploadAttachments(List<MultipartFile> attachments) {
        Map<String, String> byName = new LinkedHashMap<>();
        if (attachments == null || attachments.isEmpty()) {
            return byName;
        }

        int accepted = 0;
        for (MultipartFile att : attachments) {
            if (att == null || att.isEmpty()) {
                continue;
            }
            if (accepted >= MAX_ATTACHMENTS) {
                log.warn("附件图片超过 {} 张，多余部分已忽略", MAX_ATTACHMENTS);
                break;
            }
            String original = StrUtil.blankToDefault(att.getOriginalFilename(), "");
            if (original.isBlank()) {
                continue;
            }
            // 只收图片附件
            String lower = original.toLowerCase(Locale.ROOT);
            boolean isImageName = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg")
                    || lower.endsWith(".bmp") || lower.endsWith(".avif");
            String type = StrUtil.blankToDefault(att.getContentType(), "").toLowerCase(Locale.ROOT);
            boolean isImageType = type.startsWith("image/");
            if (!isImageName && !isImageType) {
                continue;
            }
            if (att.getSize() > MAX_ATTACH_BYTES) {
                log.warn("附件过大已跳过: {} ({} bytes)", original, att.getSize());
                continue;
            }

            try {
                ImageUploadRespDTO uploaded = imageService.uploadImageForImport(att);
                if (uploaded != null && StrUtil.isNotBlank(uploaded.getUrl())) {
                    byName.put(ArticleDocumentParser.baseName(original).toLowerCase(Locale.ROOT),
                            uploaded.getUrl());
                    accepted++;
                }
            } catch (Exception e) {
                log.warn("附件图片转存失败: {} - {}", original, e.getMessage());
            }
        }
        return byName;
    }

    private void processImages(String content,
                               Map<String, String> replacements,
                               List<ArticleImportImageDTO> results,
                               Map<String, String> companionUrls) {
        List<ArticleDocumentParser.ImageRef> refs = ArticleDocumentParser.findImageRefs(content);
        if (refs.isEmpty()) {
            return;
        }

        long deadline = System.currentTimeMillis() + IMAGE_PHASE_BUDGET_MS;
        int autoAttempted = 0;

        for (ArticleDocumentParser.ImageRef ref : refs) {
            String url = ref.url();
            if (StrUtil.isBlank(url)) {
                continue;
            }

            if (System.currentTimeMillis() > deadline) {
                replacements.put(url, "");
                results.add(failed(url, "处理超时，未自动上传，请手动补传"));
                continue;
            }

            // 附件匹配不占远程自动转存额度
            String companion = matchCompanion(url, companionUrls);
            boolean isCompanion = companion != null;
            if (!isCompanion && autoAttempted >= MAX_AUTO_IMAGES && needsRemoteUpload(url)) {
                replacements.put(url, "");
                results.add(failed(url, "图片数量较多，已跳过自动上传，请手动补传"));
                continue;
            }

            try {
                processOneImage(url, companion, deadline, replacements, results);
                if (!isCompanion && needsRemoteUpload(url)) {
                    autoAttempted++;
                }
            } catch (Exception e) {
                log.warn("导入文档图片处理失败: {}", abbreviate(url), e);
                replacements.put(url, "");
                results.add(failed(url, "处理异常：" + safeMessage(e)));
            }
        }
    }

    /** 按文件名匹配附件（忽略路径与大小写）。 */
    private static String matchCompanion(String url, Map<String, String> companionUrls) {
        if (companionUrls == null || companionUrls.isEmpty() || url == null) {
            return null;
        }
        // data:/http: 不按附件匹配
        if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) {
            return null;
        }
        String base = ArticleDocumentParser.baseName(url).toLowerCase(Locale.ROOT);
        if (base.isEmpty()) {
            return null;
        }
        return companionUrls.get(base);
    }

    /** 远程 / data URI 才计入自动转存额度 */
    private static boolean needsRemoteUpload(String url) {
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:");
    }

    private void processOneImage(String url,
                                 String companionUrl,
                                 long deadline,
                                 Map<String, String> replacements,
                                 List<ArticleImportImageDTO> results) {
        // 1) 已是本站图片
        if (isLocalUpload(url)) {
            results.add(ArticleImportImageDTO.builder()
                    .originalRef(url)
                    .status(STATUS_KEPT)
                    .url(url)
                    .build());
            return;
        }

        // 2) 用户一并选中的附件图片
        if (companionUrl != null) {
            replacements.put(url, companionUrl);
            results.add(ArticleImportImageDTO.builder()
                    .originalRef(url)
                    .status(STATUS_UPLOADED)
                    .url(companionUrl)
                    .build());
            return;
        }

        // 3) data URI
        if (url.startsWith("data:")) {
            try {
                ImageUploadRespDTO uploaded = uploadDataUri(url);
                replacements.put(url, uploaded.getUrl());
                results.add(ArticleImportImageDTO.builder()
                        .originalRef(url)
                        .status(STATUS_UPLOADED)
                        .url(uploaded.getUrl())
                        .build());
            } catch (Exception e) {
                log.warn("data URI 图片转存失败: {}", e.getMessage());
                replacements.put(url, "");
                results.add(failed(url, "内嵌图片转存失败：" + safeMessage(e)));
            }
            return;
        }

        // 4) http(s) 远程图
        if (url.startsWith("http://") || url.startsWith("https://")) {
            long remaining = Math.max(1_000L, deadline - System.currentTimeMillis());
            long timeout = Math.min(REMOTE_IMAGE_TIMEOUT_MS, remaining);
            try {
                ImageUploadRespDTO uploaded = uploadRemote(url, timeout);
                replacements.put(url, uploaded.getUrl());
                results.add(ArticleImportImageDTO.builder()
                        .originalRef(url)
                        .status(STATUS_UPLOADED)
                        .url(uploaded.getUrl())
                        .build());
            } catch (Exception e) {
                log.warn("远程图片转存失败: {} - {}", abbreviate(url), e.getMessage());
                replacements.put(url, "");
                results.add(failed(url, "远程图片上传失败：" + safeMessage(e)));
            }
            return;
        }

        // 5) 相对路径 / Obsidian 嵌入且未提供附件
        replacements.put(url, "");
        results.add(failed(url, "未找到同名附件图片，请连同图片一起导入或手动补传"));
    }

    private ImageUploadRespDTO uploadDataUri(String dataUri) {
        int comma = dataUri.indexOf(',');
        if (comma < 0) {
            throw new ClientException("内嵌图片格式无效");
        }
        String meta = dataUri.substring(5, comma);
        String payload = dataUri.substring(comma + 1);
        if (!meta.toLowerCase(Locale.ROOT).contains("base64")) {
            throw new ClientException("仅支持 base64 内嵌图片");
        }
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new ClientException("内嵌图片 base64 解码失败");
        }
        if (imageBytes.length == 0) {
            throw new ClientException("内嵌图片为空");
        }
        String contentType = "image/png";
        int semi = meta.indexOf(';');
        String mime = semi >= 0 ? meta.substring(0, semi) : meta;
        if (StrUtil.isNotBlank(mime) && mime.startsWith("image/")) {
            contentType = mime.trim();
        }
        String ext = extensionFor(contentType);
        MultipartFile multipart = new ByteArrayMultipartFile(
                "import-embedded" + ext, contentType, imageBytes);
        return imageService.uploadImageForImport(multipart);
    }

    private ImageUploadRespDTO uploadRemote(String url, long timeoutMs) throws Exception {
        if (executorShutdown.get()) {
            throw new ClientException("导入服务不可用");
        }
        CompletableFuture<ImageUploadRespDTO> future = CompletableFuture.supplyAsync(
                () -> imageService.uploadImageByUrl(url), remoteFetchExecutor);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ClientException("远程图片拉取超时");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ClientException ce) {
                throw ce;
            }
            throw new ClientException(safeMessage(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientException("导入被中断");
        }
    }

    private static boolean isLocalUpload(String url) {
        return url.startsWith("/uploads/")
                || url.contains("/uploads/images/");
    }

    private static ArticleImportImageDTO failed(String ref, String reason) {
        return ArticleImportImageDTO.builder()
                .originalRef(ref)
                .status(STATUS_FAILED)
                .reason(reason)
                .build();
    }

    private static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            default -> ".png";
        };
    }

    private static String safeMessage(Throwable e) {
        String msg = e.getMessage();
        if (StrUtil.isBlank(msg)) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 80 ? msg.substring(0, 80) : msg;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    private record ByteArrayMultipartFile(String originalFilename, String contentType, byte[] content)
            implements MultipartFile {

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content == null ? 0 : content.length;
        }

        @Override
        public byte[] getBytes() {
            return content == null ? new byte[0] : content;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(getBytes());
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), getBytes());
        }
    }
}
