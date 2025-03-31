package cn.lemonnetwork.cloud.core.controller;

import cn.lemonnetwork.cloud.core.LemonCloudCoreApplication;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "http://localhost")
public class UserController {
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

        LemonCloudCoreApplication.getUserUUID().putIfAbsent(username, UUID.randomUUID()); //如果没有token就随机一个新的喵
        UUID token = LemonCloudCoreApplication.getUserUUID().get(username);

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

        Document newUser = new Document("mail", mail)
                .append("username", username)
                .append("maxStorage", 100) //默认用户最大存储。（单位MB）
                .append("password", password);
        users.insertOne(newUser);

        LemonCloudCoreApplication.getUserUUID().putIfAbsent(username, UUID.randomUUID()); //如果没有token就随机一个新的喵
        UUID token = LemonCloudCoreApplication.getUserUUID().get(username);

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

    @GetMapping("/storage")
    @CrossOrigin(origins = "http://localhost")
    public ResponseEntity<String> storage(
            @RequestParam String token) {
        String userName = LemonCloudCoreApplication.getUsername(token);

        MongoCollection<Document> users = LemonCloudCoreApplication.getDatabase().getCollection("users");

        Document user = users.find(new Document("username", userName)).first();

        int maxSize = user.getInteger("maxStorage");

        Path userFolder = Paths.get("userFiles/" + userName + "/");

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
