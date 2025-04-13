package cn.lemonnetwork.cloud.core.controller;

import cn.lemonnetwork.cloud.core.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/file")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/share/{uuid}")
    public ResponseEntity<?> getShareInfo(@PathVariable String uuid) {
        return fileService.getShareInfo(uuid);
    }

    @GetMapping("/reportShare")
    public ResponseEntity<?> reportShare(@RequestParam String uuid, HttpServletRequest request) {
        return fileService.reportShare(uuid, request);
    }

    @PostMapping("/share")
    public ResponseEntity<?> createShare(@RequestBody Map<String, Object> requestBody) {
        return fileService.createShare(requestBody);
    }

    @GetMapping("/shareDownloader/{uuid}")
    public ResponseEntity<?> handleFileDownload(
            @PathVariable String uuid, HttpServletResponse response) throws IOException {
        return fileService.handleFileDownload(uuid, response);
    }

    @PostMapping("/createFolder")
    public ResponseEntity<?> createFolder(@RequestBody Map<String, String> requestBody) throws IOException {
        return fileService.createFolder(requestBody);
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam String path,
            @RequestParam String file,
            @RequestHeader("Authorization") String token) throws IOException {
        return fileService.downloadFile(path, file, token);
    }

    @GetMapping("/rename")
    public ResponseEntity<?> renameFile(
            @RequestParam String path,
            @RequestParam String oldName,
            @RequestParam String newName,
            @RequestParam String token) throws IOException {
        return fileService.renameFile(path, oldName, newName, token);
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteFile(@RequestBody Map<String, String> requestBody) throws IOException {
        return fileService.deleteFile(requestBody);
    }

    @GetMapping("/list")
    public ResponseEntity<?> getFileList(
            @RequestParam(value = "path", defaultValue = "") String relativePath,
            @RequestParam String token) throws IOException {
        return fileService.getFileList(relativePath, token);
    }

    @PostMapping("/upload")
    public ResponseEntity<String> handleFileUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String token,
            @RequestParam String paths) throws IOException {
        return fileService.handleFileUpload(file, token, paths);
    }
}