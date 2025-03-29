package cn.lemonnetwork.cloud.core.controller;

import cn.lemonnetwork.cloud.core.LemonCloudCoreApplication;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
public class RegisterController {
    @CrossOrigin(origins = "http://localhost:5173")
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
}
