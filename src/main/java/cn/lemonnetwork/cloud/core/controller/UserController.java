package cn.lemonnetwork.cloud.core.controller;

import cn.lemonnetwork.cloud.core.LemonCloudCoreApplication;
import cn.lemonnetwork.cloud.core.util.AddressUtil;
import cn.lemonnetwork.cloud.core.util.EmailUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.gridfs.model.GridFSFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "http://localhost")
public class UserController {
    private final GridFsTemplate gridFsTemplate; //头像的上传支持喵

    public UserController(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
    }

    private final Cache<String, String> emailCode = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build(); //存储邮箱验证码用喵

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/sendEmailCode/{email}")
    public ResponseEntity<Map<String, String>> sendEmailCode(@PathVariable String email) {
        String emailRegex = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
        if (!Pattern.matches(emailRegex, email)) {
            return ResponseEntity.ok(Map.of("message", "邮箱格式不正确"));
        }

        String existingCode = emailCode.getIfPresent(email);
        if (existingCode != null) {
            return ResponseEntity.ok(Map.of("message", "邮箱请求过快喵"));
        }

        int code = new Random().nextInt(100000, 999999);
        emailCode.put(email, String.valueOf(code));

        EmailUtil.sendEmail(email, "你的邮箱验证码", "你的邮箱验证码是: " + code +
                "\n如果这封邮箱不是你发送的, 请忽视本邮件\n\n柠檬网盘");

        return ResponseEntity.ok(Map.of("message", "发送成功"));
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/editUserEmail")
    public ResponseEntity<Map<String, String>> editUserEmail(@RequestParam String token, @RequestParam String newEmail, @RequestParam String code) { //通过Token获取用户信息喵
        String existingCode = emailCode.getIfPresent(newEmail);
        if (existingCode == null) {
            return ResponseEntity.ok(Map.of("message", "请发送验证码"));
        }

        if (!code.equals(emailCode.getIfPresent(newEmail))) {
            return ResponseEntity.ok(Map.of("message", "验证码错误"));
        }

        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

        MongoCollection<Document> collection = LemonCloudCoreApplication.getDatabase().getCollection("users");
        Document document = collection.find(new Document("uuid", userUUID.toString())).first();

        if (document.getString("email").equals(newEmail)) {
            return ResponseEntity.ok(Map.of("message", "邮箱不能与原邮箱相同"));
        }

        if (collection.find(new Document("email", newEmail)).first() != null) {
            return ResponseEntity.ok(Map.of("message", "此邮箱已被使用"));
        }

        collection.updateOne(
                new Document("uuid", userUUID.toString()),
                new Document("$set", new Document("email", newEmail)) //最主要的喵
        );
        return ResponseEntity.ok(Map.of("message", "更改成功"));
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/userLoginInfo/{token}")
    public ResponseEntity<Map<String, String>> getUserLoginToken(@PathVariable String token) { //通过Token获取用户上一次登录信息喵
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Document user = LemonCloudCoreApplication.getDatabase().getCollection("users").find(new Document("uuid", userUUID.toString())).first();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd hh:mm");

        Map<String, String> result = new HashMap<>();
        result.put("lastLoginDate", formatter.format(user.getDate("lastLoginDate")));
        result.put("lastLoginAddress", AddressUtil.get(user.getString("lastLoginAddress")));
        return ResponseEntity.ok(result);
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestParam String username, @RequestParam String password, HttpServletRequest request) {

        MongoCollection<Document> users = LemonCloudCoreApplication.getDatabase().getCollection("users");

        Document user = users.find(new Document("username", username)).first();
        if (user == null) {
            return ResponseEntity.ok(Map.of("message", "账号或密码错误"));
        }

        String storedPassword = user.getString("password");
        if (!password.equals(storedPassword)) {
            return ResponseEntity.ok(Map.of("message", "账号或密码错误"));
        }

        UUID userUUID = UUID.fromString(user.getString("uuid"));
        LemonCloudCoreApplication.getUserUUID().putIfAbsent(userUUID, UUID.randomUUID()); //如果没有token就随机一个新的喵
        UUID token = LemonCloudCoreApplication.getUserUUID().get(userUUID);

        users.updateOne(
                new Document("uuid", userUUID.toString()),
                new Document("$set", new Document("lastLoginDate", new Date())));
        users.updateOne(
                new Document("uuid", userUUID.toString()),
                new Document("$set", new Document("lastLoginAddress", AddressUtil.getClientIpAddress(request))));

        return ResponseEntity.ok(Map.of("message", "token" + token));
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestParam String mail, @RequestParam String username, @RequestParam String password, @RequestParam String code, HttpServletRequest request) {
        String existingCode = emailCode.getIfPresent(mail);
        if (existingCode == null) {
            return ResponseEntity.ok(Map.of("message", "请发送验证码"));
        }

        if (!code.equals(emailCode.getIfPresent(mail))) {
            return ResponseEntity.ok(Map.of("message", "验证码错误"));
        }

        MongoCollection<Document> users = LemonCloudCoreApplication.getDatabase().getCollection("users");

        // 检查邮箱格式是否正确喵
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        if (!Pattern.matches(emailRegex, mail)) {
            return ResponseEntity.ok(Map.of("message", "无效的邮箱格式"));
        }

        // 检查邮箱是否已存在喵
        if (users.find(new Document("mail", mail)).first() != null) {
            return ResponseEntity.ok(Map.of("message", "邮箱已存在"));
        }

        // 检查用户名是否已存在喵
        if (users.find(new Document("username", username)).first() != null) {
            return ResponseEntity.ok(Map.of("message", "用户名已存在"));
        }

        UUID userUUID = UUID.randomUUID();

        Document newUser = new Document("mail", mail)
                .append("username", username)
                .append("uuid", userUUID.toString())
                .append("maxStorage", 100) //默认用户最大存储。（单位MB）
                .append("password", password)
                .append("email", mail)
                .append("lastLoginDate", new Date())
                .append("lastLoginAddress", AddressUtil.getClientIpAddress(request))
                .append("password", password)
                .append("avatarId", "");
        users.insertOne(newUser);

        LemonCloudCoreApplication.getUserUUID().putIfAbsent(userUUID, UUID.randomUUID()); //如果没有token就随机一个新的喵
        UUID token = LemonCloudCoreApplication.getUserUUID().get(userUUID);

        return ResponseEntity.ok(Map.of("message", "token" + token));
    }

    //检查token是否有效喵。因为服务器一重启token HashMap便会重置www。此方法旨在token过期提醒前端重新登录qwq
    @GetMapping("/checkToken")
    @CrossOrigin(origins = "http://localhost")
    public ResponseEntity<Map<String, String>> checkToken(
            @RequestParam String token) {
        return ResponseEntity.ok(Map.of("message",
                (LemonCloudCoreApplication.getUserUUID().containsValue(UUID.fromString(token)) ? "Successful" : "Fail")));
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/editUserInfo")
    public ResponseEntity<Map<String, String>> editUserInfo(@RequestParam String token, @RequestParam String newUsername) { //通过Token获取用户信息喵
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

        MongoCollection<Document> collection = LemonCloudCoreApplication.getDatabase().getCollection("users");
        Document document = collection.find(new Document("uuid", userUUID.toString())).first();

        if (document.getString("username").equals(newUsername)) {
            return ResponseEntity.ok(Map.of("message", "用户名不能与原用户名相同"));
        }

        if (collection.find(new Document("username", newUsername)).first() != null) {
            return ResponseEntity.ok(Map.of("message", "此用户名已被使用"));
        }

        collection.updateOne(
                new Document("uuid", userUUID.toString()),
                new Document("$set", new Document("username", newUsername)) //最主要的喵
        );
        return ResponseEntity.ok(Map.of("message", "更改成功"));
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/editPassword")
    public ResponseEntity<Map<String, String>> editPassword(@RequestParam String token, @RequestParam String newPassword, @RequestParam String oldPassword) { //通过Token获取用户信息喵
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

        MongoCollection<Document> collection = LemonCloudCoreApplication.getDatabase().getCollection("users");
        Document document = collection.find(new Document("uuid", userUUID.toString())).first();

        if (document.getString("password").equals(newPassword)) {
            return ResponseEntity.ok(Map.of("message", "密码不能与原密码相同"));
        }

        if (!oldPassword.equals(document.getString("password"))) {
            return ResponseEntity.ok(Map.of("message", "原密码错误"));
        }

        if (!isSafePassword(newPassword)){
            return ResponseEntity.ok(Map.of("message", "密码必须包含字母数字以及满足8位"));
        }

        collection.updateOne(
                new Document("uuid", userUUID.toString()),
                new Document("$set", new Document("password", newPassword)) //最主要的喵
        );
        return ResponseEntity.ok(Map.of("message", "更改成功"));
    }

    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/userInfo/{token}")
    public String getUserInfo(@PathVariable String token) { //通过Token获取用户信息喵
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);

        Document userDoc = LemonCloudCoreApplication.getDatabase()
                .getCollection("users")
                .find(new Document("uuid", userUUID.toString()))
                .first();

        if (userDoc != null) {
            userDoc.remove("password");
            userDoc.remove("lastLoginDate");
            userDoc.remove("lastLoginAddress");
            return userDoc.toJson();
        } else {
            return "{}";
        }
    }


    @CrossOrigin(origins = "http://localhost")
    @GetMapping("/userInfoU/{uuid}")
    public String uploadAvatar(@PathVariable String uuid) { //通过UUID获取用户信息喵
        UUID userUUID = UUID.fromString(uuid);

        Document userDoc = LemonCloudCoreApplication.getDatabase()
                .getCollection("users")
                .find(new Document("uuid", userUUID.toString()))
                .first();

        if (userDoc != null) {
            userDoc.remove("password");
            userDoc.remove("lastLoginDate");
            userDoc.remove("lastLoginAddress");
            return userDoc.toJson();
        } else {
            return "{}";
        }
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

    public static boolean isSafePassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;

        for (char ch : password.toCharArray()) {
            if (Character.isLetter(ch)) {
                hasLetter = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }

        return hasLetter && hasDigit;
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
