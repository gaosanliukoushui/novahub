package com.novahub.content.controller;

import com.novahub.common.result.Result;
import com.novahub.content.service.IOssService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Tag(name = "文件上传", description = "文件上传相关接口")
public class OssController {

    private final IOssService ossService;

    @PostMapping("/image")
    @Operation(summary = "上传图片", description = "上传图片文件，返回图片URL")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = ossService.uploadImage(file);
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        result.put("filename", extractFilename(url));
        return Result.ok(result);
    }

    @PostMapping("/video")
    @Operation(summary = "上传视频", description = "上传视频文件，返回视频URL")
    public Result<Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file) {
        String url = ossService.uploadVideo(file);
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        result.put("filename", extractFilename(url));
        return Result.ok(result);
    }

    @PostMapping("/file")
    @Operation(summary = "上传通用文件", description = "上传通用文件，可指定文件夹")
    public Result<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "files/") String folder) {
        String url = ossService.uploadFile(file, folder);
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        result.put("filename", extractFilename(url));
        return Result.ok(result);
    }

    private String extractFilename(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        return url;
    }
}
