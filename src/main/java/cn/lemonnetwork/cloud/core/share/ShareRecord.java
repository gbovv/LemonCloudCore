package cn.lemonnetwork.cloud.core.share;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

public class ShareRecord {
    public static Date calculateExpireTime(int expireType) {
        return switch (expireType) {
            case 0 -> Date.from(LocalDateTime.now().plusDays(1)
                    .atZone(ZoneId.systemDefault()).toInstant());
            case 1 -> Date.from(LocalDateTime.now().plusDays(7)
                    .atZone(ZoneId.systemDefault()).toInstant());
            case 2 -> Date.from(LocalDateTime.now().plusMonths(1)
                    .atZone(ZoneId.systemDefault()).toInstant());
            case 3 -> null;
            default -> throw new IllegalArgumentException("要...要被❤...塞坏了啦!❤ (指接口)");
        };
    }

    private String id;

    private String creator;
    private String filePath;
    private Boolean isDirectory;
    private Date created;
    private Date expire;

    public ShareRecord() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Boolean getIsDirectory() { return isDirectory; }
    public void setIsDirectory(Boolean directory) { isDirectory = directory; }

    public Date getCreated() { return created; }
    public void setCreated(Date created) { this.created = created; }

    public Date getExpire() { return expire; }
    public void setExpire(Date expire) { this.expire = expire; }
}