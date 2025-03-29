package cn.lemonnetwork.cloud.core;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
public class LemonCloudCoreApplication {

    //数据库相关变量喵
    private static MongoDatabase database;

    private static MongoClient client;

    //本喵也不知道token具体怎么实现喵，用hashmap实现一下啦( •̀ ω •́ )✧
    private static HashMap<String, UUID> userUUID = new HashMap<>();

    public static void main(String[] args) {
        SpringApplication.run(LemonCloudCoreApplication.class, args);

        client = MongoClients.create("mongodb://localhost:27017");
        database = client.getDatabase("lemoncloud"); //初始化数据库变量喵
    }

    public static MongoClient getClient() {
        return client;
    }

    public static MongoDatabase getDatabase() {
        return database;
    }

    public static HashMap<String, UUID> getUserUUID() {
        return userUUID;
    }

    public static String getUsername(String token) {
        return getKeyByValue(getUserUUID(), UUID.fromString(token));
    }

    private static  <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null; // 未找到返回 null
    }
}
