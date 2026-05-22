package com.novahub.content.service;

import org.springframework.web.multipart.MultipartFile;

public interface IOssService {

    /**
     * 上传文件
     *
     * @param file   文件
     * @param folder 文件夹路径
     * @return 文件URL
     */
    String uploadFile(MultipartFile file, String folder);

    /**
     * 上传图片
     *
     * @param file 图片文件
     * @return 图片URL
     */
    String uploadImage(MultipartFile file);

    /**
     * 上传视频
     *
     * @param file 视频文件
     * @return 视频URL
     */
    String uploadVideo(MultipartFile file);

    /**
     * 获取文件URL
     *
     * @param filename 文件名
     * @return 文件URL
     */
    String getFileUrl(String filename);
}
