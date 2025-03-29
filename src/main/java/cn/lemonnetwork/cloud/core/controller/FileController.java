package cn.lemonnetwork.cloud.core.controller;

import cn.lemonnetwork.cloud.core.LemonCloudCoreApplication;
import com.mongodb.client.MongoCollection;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/file")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class FileController {

    @PostMapping("/createFolder")
    @CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
    public ResponseEntity<?> createFolder(
            @RequestBody Map<String, String> requestBody) {
        String userName = LemonCloudCoreApplication.getUsername(requestBody.get("token"));

        try {
            String relativePath = requestBody.get("path");
            String folderName = requestBody.get("folderName");

            if (folderName == null || folderName.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "文件夹名称不能为空"));
            }

            Path dirPath = Paths.get("userFiles/" + userName + "/" + (relativePath.isEmpty() ? "" : relativePath + "/")  + sanitizeFolderName(folderName));

            if (Files.exists(dirPath)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "目录已存在"));
            }


            Files.createDirectories(dirPath);
            return ResponseEntity.ok(Map.of(
                    "message", "目录创建成功",
                    "path", dirPath.toString()
            ));

        } catch (InvalidPathException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "包含非法字符"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "目录创建失败：" + e.getMessage()));
        }
    }

    @GetMapping("/download")
    @CrossOrigin(origins = "http://localhost:5173")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam String path,
            @RequestParam String file,
            @RequestParam String token) {
        String userName = LemonCloudCoreApplication.getUsername(token);

        try {
            Path filePath = Paths.get("userFiles/" + userName + "/" + (path.isEmpty() ? "" : path + "/") + file);

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .contentType(MediaType.parseMediaType(
                            contentType != null ? contentType : "application/octet-stream"))
                    .body(resource);

        } catch (InvalidPathException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }


    @PostMapping("/delete")
    @CrossOrigin(origins = "http://localhost:5173")
    public ResponseEntity<?> deleteFile(
            @RequestBody Map<String, String> requestBody) {
        String userName = LemonCloudCoreApplication.getUsername(requestBody.get("token"));

        try {
            Path targetPath = Paths.get("userFiles/" + userName + "/" + (requestBody.get("path").isEmpty() ? "" : requestBody.get("path") + "/") + requestBody.get("name"));

            if (!Files.exists(targetPath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "文件不存在"));
            }

            if (requestBody.get("type").equals("dir")) {
                deleteDirectoryRecursively(targetPath);
            } else {
                Files.delete(targetPath);
            }

            return ResponseEntity.ok(Map.of("message", "删除成功"));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除失败: " + e.getMessage()));
        }
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); }
                    catch (IOException e) { throw new RuntimeException(e); }
                });
    }

    private String sanitizeFolderName(String name) {
        return name.trim();
    }

    @GetMapping("/list")
    @CrossOrigin(origins = "http://localhost:5173")
    public ResponseEntity<?> getFileList(
            @RequestParam(value = "path", defaultValue = "") String relativePath, String token) {
        String userName = LemonCloudCoreApplication.getUsername(token);

        try {
            Path dirPath = Paths.get("userFiles/" + userName + "/" + relativePath + "/");

            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            if (!Files.isDirectory(dirPath)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "请求路径不是目录"));
            }

            List<Map<String, Object>> fileList = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                for (Path path : stream) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("name", path.getFileName().toString());
                    fileInfo.put("type", Files.isDirectory(path) ? "dir" : "file");
                    fileInfo.put("modified", Files.getLastModifiedTime(path).toMillis());
                    fileInfo.put("size", Files.size(path));
                    fileList.add(fileInfo);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "path", dirPath.toString(),
                    "files", fileList
            ));

        } catch (InvalidPathException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "非法路径参数"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "无法读取目录内容"));
        }
    }

    @PostMapping("/upload")
    @CrossOrigin(origins = "http://localhost:5173")
    public ResponseEntity<String> handleFileUpload(
            @RequestParam("file") MultipartFile file, String token, String paths) {


        String userName = LemonCloudCoreApplication.getUsername(token);

        try {
            Path uploadPath = Paths.get("userFiles/" + userName + "/" + paths + "/");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            MongoCollection<Document> users = LemonCloudCoreApplication.getDatabase().getCollection("users");

            Document user = users.find(new Document("username", userName)).first();

            long fileSize = file.getSize();
            long maxSize = user.getInteger("maxStorage") * 1024 * 1024;

            Path userFolder = Paths.get("userFiles/" + userName + "/");

            long totalSize = Files.walk(userFolder)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();

            if (totalSize + fileSize > maxSize) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body("{\"message\": \"Maxed\"}");
            }

            String filename = file.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok("{\"message\": \"Successful\"}");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed");
        }
    }
}
