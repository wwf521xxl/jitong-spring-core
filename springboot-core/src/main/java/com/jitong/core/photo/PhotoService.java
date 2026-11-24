package com.jitong.core.photo;

import com.jitong.core.common.ApiException;
import com.jitong.core.common.StoragePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private static final int MAX_BYTES = 2_000_000;

    private final StoragePaths storagePaths;

    public String save(String recordId, String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        if (!dataUrl.startsWith("data:image/")) {
            throw ApiException.badRequest("现场照片格式不正确");
        }
        try {
            int comma = dataUrl.indexOf(',');
            if (comma <= 0) {
                throw ApiException.badRequest("现场照片格式不正确");
            }
            String header = dataUrl.substring(0, comma).toLowerCase();
            byte[] raw = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            if (raw.length > MAX_BYTES) {
                throw ApiException.badRequest("现场照片太大，请压缩后重试");
            }
            String extension = header.contains("png") ? "png" : "jpg";
            Files.write(storagePaths.getPhotosDir().resolve(recordId + "." + extension), raw);
            return recordId + "." + extension;
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("现场照片格式不正确");
        } catch (IOException ex) {
            throw new IllegalStateException("保存现场照片失败", ex);
        }
    }

    public Resource load(String filename) {
        if (filename == null || filename.isBlank()
                || !Path.of(filename).getFileName().toString().equals(filename)) {
            throw ApiException.notFound("not_found", "照片不存在");
        }
        Path path = storagePaths.getPhotosDir().resolve(filename).normalize();
        if (!path.startsWith(storagePaths.getPhotosDir()) || !Files.isRegularFile(path)) {
            throw ApiException.notFound("not_found", "照片不存在");
        }
        return new FileSystemResource(path);
    }
}
