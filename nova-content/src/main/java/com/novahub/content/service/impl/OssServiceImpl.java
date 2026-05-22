package com.novahub.content.service.impl;

import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.content.service.IOssService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssServiceImpl implements IOssService {

    private final MinioClient minioClient;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.bucket-name:nova-hub}")
    private String bucketName;

    private static final String IMAGE_FOLDER = "images/";
    private static final String VIDEO_FOLDER = "videos/";
    private static final String FILE_FOLDER = "files/";
    private static final String AVATAR_FOLDER = "avatars/";
    private static final String COVER_FOLDER = "covers/";
    private static final String CONTENT_FOLDER = "content/";

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        validateFile(file);
        String objectName = generateObjectName(folder, getExtension(file.getOriginalFilename()));
        uploadToMinio(file, objectName);
        return buildFileUrl(objectName);
    }

    @Override
    public String uploadImage(MultipartFile file) {
        validateFile(file);
        validateImage(file);
        String objectName = generateObjectName(CONTENT_FOLDER + IMAGE_FOLDER, getExtension(file.getOriginalFilename()));
        uploadToMinio(file, objectName);
        return buildFileUrl(objectName);
    }

    @Override
    public String uploadVideo(MultipartFile file) {
        validateFile(file);
        validateVideo(file);
        String objectName = generateObjectName(CONTENT_FOLDER + VIDEO_FOLDER, getExtension(file.getOriginalFilename()));
        uploadToMinio(file, objectName);
        return buildFileUrl(objectName);
    }

    @Override
    public String getFileUrl(String filename) {
        return buildFileUrl(filename);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件大小不能超过100MB");
        }
    }

    private void validateImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/"))) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "只能上传图片文件");
        }
    }

    private void validateVideo(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("video/"))) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "只能上传视频文件");
        }
    }

    private String generateObjectName(String folder, String extension) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        return folder + timestamp + "_" + uuid + extension;
    }

    private String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            return filename.substring(dotIndex);
        }
        return "";
    }

    private void uploadToMinio(MultipartFile file, String objectName) {
        try (InputStream inputStream = file.getInputStream()) {
            ensureBucketExists();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("文件上传成功: bucket={}, object={}, size={}",
                    bucketName, objectName, file.getSize());
        } catch (Exception e) {
            log.error("文件上传失败: object={}", objectName, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR, "文件上传失败: " + e.getMessage());
        }
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        io.minio.MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("创建MinIO存储桶: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("检查/创建存储桶失败: bucket={}", bucketName, e);
        }
    }

    private String buildFileUrl(String objectName) {
        if (endpoint != null && endpoint.endsWith("/")) {
            return endpoint + bucketName + "/" + objectName;
        }
        return endpoint + "/" + bucketName + "/" + objectName;
    }
}
