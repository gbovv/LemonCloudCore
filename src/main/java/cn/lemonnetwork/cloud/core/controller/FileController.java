package cn.lemonnetwork.cloud.core.controller;

import cn.lemonnetwork.cloud.core.LemonCloudCoreApplication;
import cn.lemonnetwork.cloud.core.share.ShareRecord;
import cn.lemonnetwork.cloud.core.share.ShareService;
import cn.lemonnetwork.cloud.core.util.AddressUtil;
import cn.lemonnetwork.cloud.core.util.EmailUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.client.MongoCollection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/file")
@CrossOrigin(origins = {"http://localhost"})
public class FileController {
    private final ShareService shareService = new ShareService();

    @GetMapping("/share/{uuid}")
    @CrossOrigin(origins = {"http://localhost"})
    public ResponseEntity<?> getShareInfo(@PathVariable String uuid) {
        ShareRecord record = shareService.findById(uuid);

        if (record == null) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(Map.of("message", "分享的内容不存在"));
        }

        return ResponseEntity.ok(record);
    }

    private final Cache<String, String> reporters = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    @GetMapping("/reportShare")
    @CrossOrigin(origins = {"http://localhost"})
    public ResponseEntity<?> reportShare(@RequestParam String uuid, HttpServletRequest response) {
        ShareRecord record = shareService.findById(uuid);

        if (reporters.getIfPresent(AddressUtil.getClientIpAddress(response)) != null) {

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(Map.of("message", "举报过于频繁"));
        }

        if (record == null) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(Map.of("message", "分享的内容不存在"));
        }

        System.out.println("一个资源被举报, UUID: " + uuid);
        EmailUtil.sendEmail(EmailUtil.admin, "有一个分享的文件被举报", "举报文件UUID: " + uuid + "\n柠檬网盘");

        return ResponseEntity.ok(Map.of("message", "举报成功"));
    }

    @PostMapping("/share")
    @CrossOrigin(origins = {"http://localhost"})
    public ResponseEntity<?> createShare(
            @RequestBody Map<String, Object> requestBody) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID((String) requestBody.get("token"));

        ShareRecord record = new ShareRecord();
        record.setId(UUID.randomUUID().toString());
        record.setCreator(userUUID.toString());
        record.setFilePath(requestBody.get("path") + "/" + requestBody.get("file"));
        record.setIsDirectory((Boolean) requestBody.get("isDirectory"));
        record.setCreated(new Date());
        record.setExpire(ShareRecord.calculateExpireTime((Integer) requestBody.get("expireType")));

        shareService.createShare(record);

        return ResponseEntity.ok(Map.of("uuid", record.getId()));
    }

    @GetMapping("/shareDownloader/{uuid}")
    @CrossOrigin(origins = {"http://localhost"})
    public ResponseEntity<?> handleFileDownload(
            @PathVariable String uuid,
            HttpServletResponse response) {

        try {
            ShareRecord record = shareService.findById(uuid);
            if (record == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("分享链接不存在或已失效了喵");
            }

            if (record.getExpire() != null &&
                    (new Date()).toInstant().isAfter(record.getExpire().toInstant())) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .body("分享链接过期了喵");
            }

            Path filePath = Paths.get("userFiles/" + record.getCreator() + "/" + record.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("文件被本喵吃掉惹");
            }

            if (record.getIsDirectory()) {
                String zipName = filePath.getFileName() + ".zip";
                response.setContentType("application/zip");
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodeFilename(zipName) + "\"");

                try (ZipOutputStream zos = new ZipOutputStream(
                        new BufferedOutputStream(response.getOutputStream()))) {

                    Files.walk(filePath)
                            .filter(path -> !Files.isDirectory(path))
                            .forEach(path -> {
                                try {
                                    String relativePath = filePath.relativize(path).toString();
                                    zos.putNextEntry(new ZipEntry(relativePath));
                                    Files.copy(path, zos);
                                    zos.closeEntry();
                                } catch (IOException e) {
                                    throw new RuntimeException("压缩文件失败: " + path, e);
                                }
                            });
                }
            } else {
                response.setContentType(Files.probeContentType(filePath));
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodeFilename(filePath.getFileName().toString()) + "\"");

                Files.copy(filePath, response.getOutputStream());
            }

            return ResponseEntity.ok().build();

        } catch (InvalidPathException e) {
            return ResponseEntity.badRequest()
                    .body("你想干嘛!");
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("泥没有权限喵");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(e.getMessage());
        }
    }

    private String encodeFilename(String filename) {
        try {
            return new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            return filename;
        }
    }

    @PostMapping("/createFolder")
    @CrossOrigin(origins = {"http://localhost"})
    public ResponseEntity<?> createFolder(
            @RequestBody Map<String, String> requestBody) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(requestBody.get("token"));

        try {
            String relativePath = requestBody.get("path");
            String folderName = requestBody.get("folderName");

            if (folderName == null || folderName.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "文件夹名称不能为空"));
            }

            Path dirPath = Paths.get("userFiles/" + userUUID + "/" + (relativePath.isEmpty() ? "" : relativePath + "/")  + sanitizeFolderName(folderName));

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
    @CrossOrigin(origins = "http://localhost")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam String path,
            @RequestParam String file,
            @RequestHeader("Authorization") String token) {

        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

        try {
            Path filePath = Paths.get("userFiles/" + userUUID + "/" + (path.isEmpty() ? "" : path + "/") + file);

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodeFilename(resource.getFilename()) + "\"")
                    .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                    .body(resource);

        } catch (InvalidPathException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }


    @GetMapping("/rename")
    @CrossOrigin(origins = "http://localhost")
    public ResponseEntity<?> renameFile(
            @RequestParam String path,
            @RequestParam String oldName,
            @RequestParam String newName,
            @RequestParam String token) {

        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

        try {
            Path sourcePath = Paths.get("userFiles/" + userUUID + "/"
                    + (path.isEmpty() ? "" : path + "/")
                    + oldName);
            Path targetPath = sourcePath.resolveSibling(newName);

            if (!Files.exists(sourcePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "文件不存在"));
            }

            if (Files.exists(targetPath)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "目标文件已存在"));
            }

            Files.move(sourcePath, targetPath);

            return ResponseEntity.ok(Map.of("message", "重命名成功"));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/delete")
    @CrossOrigin(origins = "http://localhost")
    public ResponseEntity<?> deleteFile(
            @RequestBody Map<String, String> requestBody) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(requestBody.get("token"));

        try {
            Path targetPath = Paths.get("userFiles/" + userUUID + "/" + (requestBody.get("path").isEmpty() ? "" : requestBody.get("path") + "/") + requestBody.get("name"));

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
    @CrossOrigin(origins = "http://localhost")
    public ResponseEntity<?> getFileList(
            @RequestParam(value = "path", defaultValue = "") String relativePath, String token) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

        try {
            Path dirPath = Paths.get("userFiles/" + userUUID + "/" + relativePath + "/");

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
    @CrossOrigin(origins = "http://localhost")
    public ResponseEntity<String> handleFileUpload(
            @RequestParam("file") MultipartFile file, String token, String paths) {


        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

        try {
            Path uploadPath = Paths.get("userFiles/" + userUUID + "/" + paths + "/");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            MongoCollection<Document> users = LemonCloudCoreApplication.getDatabase().getCollection("users");

            Document user = users.find(new Document("uuid", userUUID.toString())).first();

            long fileSize = file.getSize();
            long maxSize = 0;
            if (user != null) {
                maxSize = user.getInteger("maxStorage");
            }

            Path userFolder = Paths.get("userFiles/" + userUUID + "/");

            long totalSize = (Files.walk(userFolder)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p) / 1048576;
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum());

            if (totalSize + fileSize > maxSize) {
                //System.out.println("调试喵 添加后: " + (totalSize + fileSize) + " 总大小: " + maxSize);

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
