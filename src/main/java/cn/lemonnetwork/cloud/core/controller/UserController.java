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

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {
    @GetMapping("/storage")
    @CrossOrigin(origins = "http://localhost:5173")
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
