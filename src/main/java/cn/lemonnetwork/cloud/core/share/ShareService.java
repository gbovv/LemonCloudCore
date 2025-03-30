package cn.lemonnetwork.cloud.core.share;

import cn.lemonnetwork.cloud.core.LemonCloudCoreApplication;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ShareService {
    public String createShare(ShareRecord record) {
        record.setId(UUID.randomUUID().toString());
        insert(record);
        return record.getId();
    }

    public ShareRecord findById(String uuid) {
        Document doc = collection.find(Filters.eq("_id", uuid)).first();
        if (doc == null) return null;

        ShareRecord record = new ShareRecord();
        record.setId(doc.getString("_id"));
        record.setUsername(doc.getString("username"));
        record.setFilePath(doc.getString("filePath"));
        record.setIsDirectory(doc.getBoolean("isDirectory"));
        record.setCreated(doc.get("created", Date.class));
        record.setExpire(doc.get("expire", Date.class));
        return record;
    }

    public int cleanExpiredShares() {
        LocalDateTime now = LocalDateTime.now();
        List<ShareRecord> expired = findExpired(now);
        deleteExpired(now);
        return expired.size();
    }

    private static MongoCollection<Document> collection;

    public static void loadCollection() {
        collection = LemonCloudCoreApplication.getDatabase().getCollection("shares");
    }

    public void insert(ShareRecord record) {
        Document doc = new Document("_id", record.getId())
                .append("username", record.getUsername())
                .append("filePath", record.getFilePath())
                .append("isDirectory", record.getIsDirectory())
                .append("created", record.getCreated())
                .append("expire", record.getExpire());
        collection.insertOne(doc);
    }

    public List<ShareRecord> findExpired(LocalDateTime now) {
        Bson filter = Filters.lt("expire", now);
        List<ShareRecord> records = new ArrayList<>();
        for (Document doc : collection.find(filter)) {
            ShareRecord record = new ShareRecord();
            record.setId(doc.getString("_id"));
            record.setUsername(doc.getString("username"));
            record.setFilePath(doc.getString("filePath"));
            record.setIsDirectory(doc.getBoolean("isDirectory"));
            record.setCreated(doc.get("created", Date.class));
            record.setExpire(doc.get("expire", Date.class));
            records.add(record);
        }
        return records;
    }

    public void deleteExpired(LocalDateTime now) {
        Bson filter = Filters.lt("expire", now);
        collection.deleteMany(filter);
    }
}