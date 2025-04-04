package cn.lemonnetwork.cloud.core.controller;

import cn.lemonnetwork.cloud.core.LemonCloudCoreApplication;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.gridfs.model.GridFSFile;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "http://localhost")
public class UserController {
    private final GridFsTemplate gridFsTemplate; //头像的上传支持喵

    public UserController(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password) {

        MongoCollection<Document> users = LemonCloudCoreApplication.getDatabase().getCollection("users");

        Document user = users.find(new Document("username", username)).first();
        if (user == null) {
            return "{\"message\": \"Invalid user\"}";
        }

        String storedPassword = user.getString("password");
        if (!password.equals(storedPassword)) {
            return "{\"message\": \"Invalid user\"}";
        }

        UUID userUUID = UUID.fromString(user.getString("uuid"));
        LemonCloudCoreApplication.getUserUUID().putIfAbsent(userUUID, UUID.randomUUID()); //如果没有token就随机一个新的喵
        UUID token = LemonCloudCoreApplication.getUserUUID().get(userUUID);

        return "{\"message\": \"" + token + "\"}";
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/register")
    public ResponseEntity<String> register(@RequestParam String mail, @RequestParam String username, @RequestParam String password) {
        MongoCollection<Document> users = LemonCloudCoreApplication.getDatabase().getCollection("users");

        // 检查邮箱格式是否正确喵
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        if (!Pattern.matches(emailRegex, mail)) {
            return ResponseEntity.ok("{\"message\": \"Invalid email format\"}");
        }

        // 检查邮箱是否已存在喵
        if (users.find(new Document("mail", mail)).first() != null) {
            return ResponseEntity.ok("{\"message\": \"Already exists\"}");
        }

        // 检查用户名是否已存在喵
        if (users.find(new Document("username", username)).first() != null) {
            return ResponseEntity.ok("{\"message\": \"Username occupied\"}");
        }

        UUID userUUID = UUID.randomUUID();

        Document newUser = new Document("mail", mail)
                .append("username", username)
                .append("uuid", userUUID.toString())
                .append("maxStorage", 100) //默认用户最大存储。（单位MB）
                .append("password", password)
                .append("avatarId", "");
        users.insertOne(newUser);

        LemonCloudCoreApplication.getUserUUID().putIfAbsent(userUUID, UUID.randomUUID()); //如果没有token就随机一个新的喵
        UUID token = LemonCloudCoreApplication.getUserUUID().get(userUUID);

        return ResponseEntity.ok("{\"message\": \"Successful\", \"token\": \"" + token + "\"}");
    }

    //检查token是否有效喵。因为服务器一重启token HashMap便会重置www。此方法旨在token过期提醒前端重新登录qwq
    @GetMapping("/checkToken")
    @CrossOrigin(origins = "http://localhost")
    public ResponseEntity<String> checkToken(
            @RequestParam String token) {
        return ResponseEntity.ok("{\"message\": \"" +
                (LemonCloudCoreApplication.getUserUUID().containsValue(UUID.fromString(token)) ? "Successful" : "Fail") +"\"}");
    }


    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/userInfo/{token}")
    public String getUserInfo(@PathVariable String token) { //通过Token获取用户信息喵
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

        return LemonCloudCoreApplication.getDatabase().getCollection("users").find(new Document("uuid", userUUID.toString())).first().toJson();
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/userInfoU/{uuid}")
    public String uploadAvatar(@PathVariable String uuid) { //通过UUID获取用户信息喵
        UUID userUUID = UUID.fromString(uuid);

        return LemonCloudCoreApplication.getDatabase().getCollection("users").find(new Document("uuid", userUUID.toString())).first().toJson();
    }


    @CrossOrigin(origins = "http://localhost")
    @PostMapping("/upload-avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestParam("avatar") MultipartFile file,
            @RequestHeader("Authorization") String token) {

        if (!Objects.requireNonNull(file.getContentType()).startsWith("image/")) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "仅支持图片文件")
            );
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "文件大小不能超过2MB")
            );
        }

        try {
            UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

            MongoCollection<Document> users = LemonCloudCoreApplication.getDatabase().getCollection("users");
            Document user = users.find(new Document("uuid", userUUID.toString())).first();

            String oldAvatarId = user.getString("avatarId");
            if (oldAvatarId != null && !oldAvatarId.isEmpty()) {
                gridFsTemplate.delete(new Query(Criteria.where("_id").is(new ObjectId(oldAvatarId))));
            }

            ObjectId fileId = gridFsTemplate.store(
                    file.getInputStream(),
                    userUUID + "_avatar",
                    file.getContentType()
            );

            users.updateOne(
                    new Document("uuid", userUUID.toString()),
                    new Document("$set", new Document("avatarId", fileId.toString()))
            );

            return ResponseEntity.ok().body(Map.of(
                    "message", "头像上传成功",
                    "avatarId", fileId.toString()
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "头像上传失败: " + e.getMessage())
            );
        }
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/avatar/{id}")
    public void getAvatar(@PathVariable String id, HttpServletResponse response) {
        try {
            GridFSFile file = gridFsTemplate.findOne(new Query(Criteria.where("_id").is(new ObjectId(id))));

            response.setContentType(file.getMetadata().getString("_contentType"));
            GridFsResource resource = gridFsTemplate.getResource(file);

            try (InputStream in = resource.getInputStream()) {
                IOUtils.copy(in, response.getOutputStream());
                response.flushBuffer();
            }
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @GetMapping("/storage")
    @CrossOrigin(origins = "http://localhost")
    public ResponseEntity<String> storage(
            @RequestParam String token) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

        MongoCollection<Document> users = LemonCloudCoreApplication.getDatabase().getCollection("users");

        Document user = users.find(new Document("uuid", userUUID.toString())).first();

        int maxSize = user.getInteger("maxStorage");

        Path userFolder = Paths.get("userFiles/" + userUUID + "/");

        try {
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


            return ResponseEntity.ok().body(formatSizeForByte(totalSize) + " / " + formatSizeForMB(maxSize));
        } catch (IOException ignored) {
        }

        return ResponseEntity.ok("无法获取喵");

    }

    public static String formatSizeForByte(long sizeInBytes) {
        double sizeInKB = sizeInBytes / 1024.0;
        double sizeInMB = sizeInKB / 1024.0;
        double sizeInGB = sizeInMB / 1024.0;

        DecimalFormat df = new DecimalFormat("#.##");

        if (sizeInGB >= 1) {
            return df.format(sizeInGB) + " GB";
        } else if (sizeInMB >= 1) {
            return df.format(sizeInMB) + " MB";
        } else {
            return df.format(sizeInKB) + " KB";
        }
    }

    public static String formatSizeForMB(long sizeInMB) {
        double sizeInGB = sizeInMB / 1024.0;

        DecimalFormat df = new DecimalFormat("#.##");

        if (sizeInGB >= 1) {
            return df.format(sizeInGB) + "GB";
        } else {
            return df.format(sizeInMB) + "MB";
        }
    }
}
