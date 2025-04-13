package cn.lemonnetwork.cloud.core.service;

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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {
    private final ShareService shareService;
    private final Cache<String, String> reporters;
    private final MongoCollection<Document> usersCollection;

    public FileService() {
        this.shareService = new ShareService();
        this.reporters = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build();
        this.usersCollection = LemonCloudCoreApplication.getDatabase().getCollection("users");
    }

    public ResponseEntity<?> getShareInfo(String uuid) {
        ShareRecord record = shareService.findById(uuid);
        return record == null ?
                ResponseEntity.ok(Map.of("message", "分享的内容不存在")) :
                ResponseEntity.ok(record);
    }

    public ResponseEntity<?> reportShare(String uuid, HttpServletRequest request) {
        if (reporters.getIfPresent(AddressUtil.getClientIpAddress(request)) != null) {
            return ResponseEntity.ok(Map.of("message", "举报过于频繁"));
        }

        ShareRecord record = shareService.findById(uuid);
        if (record == null) {
            return ResponseEntity.ok(Map.of("message", "分享的内容不存在"));
        }

        EmailUtil.sendEmail(EmailUtil.admin, "有一个分享的文件被举报", "举报文件UUID: " + uuid + "\n柠檬网盘");
        return ResponseEntity.ok(Map.of("message", "举报成功"));
    }

    public ResponseEntity<?> createShare(Map<String, Object> requestBody) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID((String) requestBody.get("token"));
        ShareRecord record = createShareRecord(requestBody, userUUID);
        shareService.createShare(record);
        return ResponseEntity.ok(Map.of("uuid", record.getId()));
    }

    public ResponseEntity<?> handleFileDownload(String uuid, HttpServletResponse response) throws IOException {
        ShareRecord record = shareService.findById(uuid);
        if (record == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("分享链接不存在或已失效了喵");
        }

        if (isExpired(record)) {
            return ResponseEntity.status(HttpStatus.GONE).body("分享链接过期了喵");
        }

        Path filePath = getSharedFilePath(record);
        if (!Files.exists(filePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文件被本喵吃掉惹");
        }

        if (record.getIsDirectory()) {
            return handleDirectoryDownload(filePath, response);
        } else {
            return handleFileDownload(String.valueOf(filePath), response);
        }
    }

    public ResponseEntity<?> createFolder(Map<String, String> requestBody) throws IOException {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(requestBody.get("token"));
        Path dirPath = getValidatedFolderPath(userUUID, requestBody);

        if (Files.exists(dirPath)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "目录已存在"));
        }

        Files.createDirectories(dirPath);
        return ResponseEntity.ok(Map.of("message", "目录创建成功", "path", dirPath.toString()));
    }

    public ResponseEntity<Resource> downloadFile(String path, String file, String token) throws IOException {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Path filePath = getUserFilePath(userUUID, path, file);

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        return buildDownloadResponse(resource, filePath);
    }

    public ResponseEntity<?> renameFile(String path, String oldName, String newName, String token) throws IOException {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Path sourcePath = getUserFilePath(userUUID, path, oldName);
        Path targetPath = sourcePath.resolveSibling(newName);

        validateRenameOperation(sourcePath, targetPath);
        Files.move(sourcePath, targetPath);
        return ResponseEntity.ok(Map.of("message", "重命名成功"));
    }

    public ResponseEntity<?> deleteFile(Map<String, String> requestBody) throws IOException {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(requestBody.get("token"));
        Path targetPath = getUserFilePath(userUUID, requestBody.get("path"), requestBody.get("name"));

        if (!Files.exists(targetPath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "文件不存在"));
        }

        if (requestBody.get("type").equals("dir")) {
            deleteDirectoryRecursively(targetPath);
        } else {
            Files.delete(targetPath);
        }
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    public ResponseEntity<?> getFileList(String relativePath, String token) throws IOException {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Path dirPath = getUserDirectoryPath(userUUID, relativePath);

        List<Map<String, Object>> fileList = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            for (Path path : stream) {
                fileList.add(createFileInfo(path));
            }
        }

        return ResponseEntity.ok(Map.of("path", dirPath.toString(), "files", fileList));
    }

    public ResponseEntity<String> handleFileUpload(MultipartFile file, String token, String paths) throws IOException {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        validateStorageLimit(userUUID, file.getSize());

        Path uploadPath = getUserDirectoryPath(userUUID, paths);
        Path filePath = uploadPath.resolve(file.getOriginalFilename());
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return ResponseEntity.ok("{\"message\": \"Successful\"}");
    }

    // Helper methods
    private ShareRecord createShareRecord(Map<String, Object> requestBody, UUID userUUID) {
        ShareRecord record = new ShareRecord();
        record.setId(UUID.randomUUID().toString());
        record.setCreator(userUUID.toString());
        record.setFilePath(requestBody.get("path") + "/" + requestBody.get("file"));
        record.setIsDirectory((Boolean) requestBody.get("isDirectory"));
        record.setCreated(new Date());
        record.setExpire(ShareRecord.calculateExpireTime((Integer) requestBody.get("expireType")));
        return record;
    }

    private boolean isExpired(ShareRecord record) {
        return record.getExpire() != null && new Date().after(record.getExpire());
    }

    private Path getSharedFilePath(ShareRecord record) {
        return Paths.get("userFiles/" + record.getCreator() + "/" + record.getFilePath());
    }

    private ResponseEntity<?> handleDirectoryDownload(Path dirPath, HttpServletResponse response) throws IOException {
        String zipName = dirPath.getFileName() + ".zip";
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodeFilename(zipName) + "\"");

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(response.getOutputStream()))) {
            Files.walk(dirPath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> addToZip(zos, dirPath, path));
        }
        return ResponseEntity.ok().build();
    }

    private void addToZip(ZipOutputStream zos, Path rootPath, Path filePath) {
        try {
            String relativePath = rootPath.relativize(filePath).toString();
            zos.putNextEntry(new ZipEntry(relativePath));
            Files.copy(filePath, zos);
            zos.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException("压缩文件失败: " + filePath, e);
        }
    }

    private Path getValidatedFolderPath(UUID userUUID, Map<String, String> requestBody) {
        String folderName = requestBody.get("folderName");
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("文件夹名称不能为空");
        }

        String relativePath = requestBody.get("path");
        return Paths.get("userFiles/" + userUUID + "/" +
                (relativePath.isEmpty() ? "" : relativePath + "/") +
                folderName.trim());
    }

    private Path getUserFilePath(UUID userUUID, String path, String file) {
        return Paths.get("userFiles/" + userUUID + "/" +
                (path.isEmpty() ? "" : path + "/") + file);
    }

    private ResponseEntity<Resource> buildDownloadResponse(Resource resource, Path filePath) throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodeFilename(resource.getFilename()) + "\"")
                .contentType(MediaType.parseMediaType(
                        Files.probeContentType(filePath) != null ?
                                Files.probeContentType(filePath) : "application/octet-stream"))
                .body(resource);
    }

    private void validateRenameOperation(Path sourcePath, Path targetPath) throws IOException {
        if (!Files.exists(sourcePath)) {
            throw new NoSuchFileException("文件不存在");
        }
        if (Files.exists(targetPath)) {
            throw new FileAlreadyExistsException("目标文件已存在");
        }
    }

    private Path getUserDirectoryPath(UUID userUUID, String relativePath) throws IOException {
        Path dirPath = Paths.get("userFiles/" + userUUID + "/" + relativePath + "/");
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        return dirPath;
    }

    private Map<String, Object> createFileInfo(Path path) throws IOException {
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("name", path.getFileName().toString());
        fileInfo.put("type", Files.isDirectory(path) ? "dir" : "file");
        fileInfo.put("modified", Files.getLastModifiedTime(path).toMillis());
        fileInfo.put("size", Files.size(path));
        return fileInfo;
    }

    private void validateStorageLimit(UUID userUUID, long fileSize) throws IOException {
        Document user = usersCollection.find(new Document("uuid", userUUID.toString())).first();
        if (user == null) return;

        long maxSizeMB = user.getInteger("maxStorage");
        long fileSizeMB = fileSize / 1048576;
        long usedSpaceMB = calculateUsedSpace(userUUID);

        if (usedSpaceMB + fileSizeMB > maxSizeMB) {
            throw new IOException("Maxed");
        }
    }

    private long calculateUsedSpace(UUID userUUID) throws IOException {
        Path userFolder = Paths.get("userFiles/" + userUUID + "/");
        return Files.walk(userFolder)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p) / 1048576;
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
    }

    private String encodeFilename(String filename) {
        return new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); }
                    catch (IOException e) { throw new RuntimeException(e); }
                });
    }
}