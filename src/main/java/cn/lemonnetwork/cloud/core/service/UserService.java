package cn.lemonnetwork.cloud.core.service;

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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
@Service
public class UserService {
    private final MongoCollection<Document> usersCollection;
    private final GridFsTemplate gridFsTemplate;
    private final Cache<String, String> emailCode;

    public UserService(GridFsTemplate gridFsTemplate) {
        this.gridFsTemplate = gridFsTemplate;
        this.usersCollection = LemonCloudCoreApplication.getDatabase().getCollection("users");
        this.emailCode = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build();
    }

    public Map<String, String> sendEmailCode(String email) {
        if (!isValidEmail(email)) {
            return Map.of("message", "邮箱格式不正确");
        }

        if (emailCode.getIfPresent(email) != null) {
            return Map.of("message", "邮箱请求过快喵");
        }

        String code = String.valueOf(new Random().nextInt(100000, 999999));
        emailCode.put(email, code);
        EmailUtil.sendEmail(email, "你的邮箱验证码", "你的邮箱验证码是: " + code +
                "\n如果这封邮箱不是你发送的, 请忽视本邮件\n\n柠檬网盘");

        return Map.of("message", "发送成功");
    }

    public Map<String, String> editUserEmail(String token, String newEmail, String code) {
        if (!code.equals(emailCode.getIfPresent(newEmail))) {
            return Map.of("message", "验证码错误");
        }

        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Document user = getUserByUUID(userUUID);

        if (user.getString("email").equals(newEmail)) {
            return Map.of("message", "邮箱不能与原邮箱相同");
        }

        if (usersCollection.find(new Document("email", newEmail)).first() != null) {
            return Map.of("message", "此邮箱已被使用");
        }

        updateUserField(userUUID, "email", newEmail);
        return Map.of("message", "更改成功");
    }

    public Map<String, String> getUserLoginInfo(String token) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Document user = getUserByUUID(userUUID);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd hh:mm");

        return Map.of(
                "lastLoginDate", formatter.format(user.getDate("lastLoginDate")),
                "lastLoginAddress", AddressUtil.get(user.getString("lastLoginAddress"))
        );
    }

    public Map<String, String> login(String username, String password, HttpServletRequest request) {
        Document user = usersCollection.find(new Document("username", username)).first();
        if (user == null || !password.equals(user.getString("password"))) {
            return Map.of("message", "账号或密码错误");
        }

        UUID userUUID = UUID.fromString(user.getString("uuid"));
        UUID token = LemonCloudCoreApplication.getUserUUID().computeIfAbsent(userUUID, k -> UUID.randomUUID());

        updateUserField(userUUID, "lastLoginDate", new Date());
        updateUserField(userUUID, "lastLoginAddress", AddressUtil.getClientIpAddress(request));

        return Map.of("message", "token" + token);
    }

    public Map<String, String> register(String mail, String username, String password, String code, HttpServletRequest request) {
        if (!code.equals(emailCode.getIfPresent(mail))) {
            return Map.of("message", "验证码错误");
        }

        if (!isValidEmail(mail)) {
            return Map.of("message", "无效的邮箱格式");
        }

        if (usersCollection.find(new Document("mail", mail)).first() != null) {
            return Map.of("message", "邮箱已存在");
        }

        if (usersCollection.find(new Document("username", username)).first() != null) {
            return Map.of("message", "用户名已存在");
        }

        UUID userUUID = UUID.randomUUID();
        Document newUser = createNewUserDocument(mail, username, password, userUUID, request);
        usersCollection.insertOne(newUser);

        UUID token = LemonCloudCoreApplication.getUserUUID().computeIfAbsent(userUUID, k -> UUID.randomUUID());
        return Map.of("message", "token" + token);
    }

    public Map<String, String> checkToken(String token) {
        return Map.of("message",
                LemonCloudCoreApplication.getUserUUID().containsValue(UUID.fromString(token)) ? "Successful" : "Fail");
    }

    public Map<String, String> editUserInfo(String token, String newUsername) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Document user = getUserByUUID(userUUID);

        if (user.getString("username").equals(newUsername)) {
            return Map.of("message", "用户名不能与原用户名相同");
        }

        if (usersCollection.find(new Document("username", newUsername)).first() != null) {
            return Map.of("message", "此用户名已被使用");
        }

        updateUserField(userUUID, "username", newUsername);
        return Map.of("message", "更改成功");
    }

    public Map<String, String> editPassword(String token, String newPassword, String oldPassword) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Document user = getUserByUUID(userUUID);

        if (user.getString("password").equals(newPassword)) {
            return Map.of("message", "密码不能与原密码相同");
        }

        if (!oldPassword.equals(user.getString("password"))) {
            return Map.of("message", "原密码错误");
        }

        if (!isSafePassword(newPassword)) {
            return Map.of("message", "密码必须包含字母数字以及满足8位");
        }

        updateUserField(userUUID, "password", newPassword);
        return Map.of("message", "更改成功");
    }

    public String getUserInfo(String token) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Document userDoc = getUserByUUID(userUUID);
        return userDoc != null ? sanitizeUserDocument(userDoc).toJson() : "{}";
    }

    public String getUserInfoByUUID(String uuid) {
        UUID userUUID = UUID.fromString(uuid);
        Document userDoc = getUserByUUID(userUUID);
        return userDoc != null ? sanitizeUserDocument(userDoc).toJson() : "{}";
    }

    public Map<String, String> uploadAvatar(MultipartFile file, String token) throws IOException {
        if (!file.getContentType().startsWith("image/")) {
            return Map.of("error", "仅支持图片文件");
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            return Map.of("error", "文件大小不能超过2MB");
        }

        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Document user = getUserByUUID(userUUID);

        String oldAvatarId = user.getString("avatarId");
        if (oldAvatarId != null && !oldAvatarId.isEmpty()) {
            gridFsTemplate.delete(new Query(Criteria.where("_id").is(new ObjectId(oldAvatarId))));
        }

        ObjectId fileId = gridFsTemplate.store(
                file.getInputStream(),
                userUUID + "_avatar",
                file.getContentType()
        );

        updateUserField(userUUID, "avatarId", fileId.toString());
        return Map.of("message", "头像上传成功", "avatarId", fileId.toString());
    }

    public void getAvatar(String id, HttpServletResponse response) throws IOException {
        GridFSFile file = gridFsTemplate.findOne(new Query(Criteria.where("_id").is(new ObjectId(id))));
        GridFsResource resource = gridFsTemplate.getResource(file);
        if (file.getMetadata() != null) {
            response.setContentType(file.getMetadata().getString("_contentType"));
        }
        IOUtils.copy(resource.getInputStream(), response.getOutputStream());
        response.flushBuffer();
    }

    public String getStorageInfo(String token) {
        UUID userUUID = LemonCloudCoreApplication.getUserUUID(token);
        Document user = getUserByUUID(userUUID);
        int maxSize = user.getInteger("maxStorage");

        try {
            long totalSize = calculateUserStorageSize(userUUID);
            return formatSizeForByte(totalSize) + " / " + formatSizeForMB(maxSize);
        } catch (IOException e) {
            return "无法获取喵";
        }
    }

    private Document getUserByUUID(UUID userUUID) {
        return usersCollection.find(new Document("uuid", userUUID.toString())).first();
    }

    private void updateUserField(UUID userUUID, String field, Object value) {
        usersCollection.updateOne(
                new Document("uuid", userUUID.toString()),
                new Document("$set", new Document(field, value))
        );
    }

    private Document createNewUserDocument(String mail, String username, String password, UUID userUUID, HttpServletRequest request) {
        return new Document("mail", mail)
                .append("username", username)
                .append("uuid", userUUID.toString())
                .append("maxStorage", 100)
                .append("password", password)
                .append("email", mail)
                .append("lastLoginDate", new Date())
                .append("lastLoginAddress", AddressUtil.getClientIpAddress(request))
                .append("avatarId", "");
    }

    private Document sanitizeUserDocument(Document userDoc) {
        userDoc.remove("password");
        userDoc.remove("lastLoginDate");
        userDoc.remove("lastLoginAddress");
        return userDoc;
    }

    private long calculateUserStorageSize(UUID userUUID) throws IOException {
        Path userFolder = Paths.get("userFiles/" + userUUID + "/");
        return Files.walk(userFolder)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
        return Pattern.matches(emailRegex, email);
    }

    private boolean isSafePassword(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char ch : password.toCharArray()) {
            if (Character.isLetter(ch)) hasLetter = true;
            else if (Character.isDigit(ch)) hasDigit = true;
        }
        return hasLetter && hasDigit;
    }

    private String formatSizeForByte(long sizeInBytes) {
        double sizeInKB = sizeInBytes / 1024.0;
        double sizeInMB = sizeInKB / 1024.0;
        double sizeInGB = sizeInMB / 1024.0;
        DecimalFormat df = new DecimalFormat("#.##");
        if (sizeInGB >= 1) return df.format(sizeInGB) + " GB";
        if (sizeInMB >= 1) return df.format(sizeInMB) + " MB";
        return df.format(sizeInKB) + " KB";
    }

    private String formatSizeForMB(long sizeInMB) {
        double sizeInGB = sizeInMB / 1024.0;
        DecimalFormat df = new DecimalFormat("#.##");
        return sizeInGB >= 1 ? df.format(sizeInGB) + "GB" : df.format(sizeInMB) + "MB";
    }
}