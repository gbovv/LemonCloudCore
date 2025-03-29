package cn.lemonnetwork.cloud.core.controller;

import cn.lemonnetwork.cloud.core.LemonCloudCoreApplication;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.UUID;

@RestController
public class LoginController {
    @CrossOrigin(origins = "http://localhost:5173")
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
}
