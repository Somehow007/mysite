package io.github.somehow.mysite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

import java.util.Arrays;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "image.upload")
public class ImageUploadConfig {

    private String basePath = "./uploads/images";

    @DataSizeUnit(DataUnit.MEGABYTES)
    private DataSize maxFileSize = DataSize.ofMegabytes(5);

    private List<String> allowedTypes = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
    );
    private String urlPath = "/uploads/images/";
    private int maxUploadsPerMinute = 10;
    private int urlFetchConnectTimeout = 5000;
    private int urlFetchReadTimeout = 15000;

    public long getMaxFileSizeBytes() {
        return maxFileSize.toBytes();
    }
}
