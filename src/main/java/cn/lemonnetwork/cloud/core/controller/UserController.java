package cn.lemonnetwork.cloud.core.controller;

import cn.lemonnetwork.cloud.core.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/sendEmailCode/{email}")
    public ResponseEntity<Map<String, String>> sendEmailCode(@PathVariable String email) {
        return ResponseEntity.ok(userService.sendEmailCode(email));
    }

    @GetMapping("/editUserEmail")
    public ResponseEntity<Map<String, String>> editUserEmail(@RequestParam String token, @RequestParam String newEmail, @RequestParam String code) {
        return ResponseEntity.ok(userService.editUserEmail(token, newEmail, code));
    }

    @GetMapping("/userLoginInfo/{token}")
    public ResponseEntity<Map<String, String>> getUserLoginToken(@PathVariable String token) {
        return ResponseEntity.ok(userService.getUserLoginInfo(token));
    }

    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestParam String username, @RequestParam String password, HttpServletRequest request) {
        return ResponseEntity.ok(userService.login(username, password, request));
    }

    @GetMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestParam String mail, @RequestParam String username,
                                                        @RequestParam String password, @RequestParam String code, HttpServletRequest request) {
        return ResponseEntity.ok(userService.register(mail, username, password, code, request));
    }

    @GetMapping("/checkToken")
    public ResponseEntity<Map<String, String>> checkToken(@RequestParam String token) {
        return ResponseEntity.ok(userService.checkToken(token));
    }

    @GetMapping("/editUserInfo")
    public ResponseEntity<Map<String, String>> editUserInfo(@RequestParam String token, @RequestParam String newUsername) {
        return ResponseEntity.ok(userService.editUserInfo(token, newUsername));
    }

    @GetMapping("/editPassword")
    public ResponseEntity<Map<String, String>> editPassword(@RequestParam String token,
                                                            @RequestParam String newPassword, @RequestParam String oldPassword) {
        return ResponseEntity.ok(userService.editPassword(token, newPassword, oldPassword));
    }

    @GetMapping("/userInfo/{token}")
    public String getUserInfo(@PathVariable String token) {
        return userService.getUserInfo(token);
    }

    @GetMapping("/userInfoU/{uuid}")
    public String uploadAvatar(@PathVariable String uuid) {
        return userService.getUserInfoByUUID(uuid);
    }

    @PostMapping("/upload-avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestParam("avatar") MultipartFile file,
            @RequestHeader("Authorization") String token) throws IOException {
        return ResponseEntity.ok(userService.uploadAvatar(file, token));
    }

    @GetMapping("/avatar/{id}")
    public void getAvatar(@PathVariable String id, HttpServletResponse response) throws IOException {
        userService.getAvatar(id, response);
    }

    @GetMapping("/storage")
    public ResponseEntity<String> storage(@RequestParam String token) {
        return ResponseEntity.ok(userService.getStorageInfo(token));
    }
}