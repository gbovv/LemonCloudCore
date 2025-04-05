package cn.lemonnetwork.cloud.core.util;

import jakarta.servlet.http.HttpServletRequest;
import org.lionsoul.ip2region.xdb.Searcher;

import java.io.InputStream;

public class AddressUtil {
    public static String get(String address) {
        try {
            InputStream inputStream = AddressUtil.class.getClassLoader().getResourceAsStream("ip2region.xdb");

            if (inputStream == null) {
                throw new IllegalStateException("没有找到IP属地数据库喵");
            }
            Searcher searcher = Searcher.newWithBuffer(inputStream.readAllBytes());
            String region = searcher.search(address);

            return region.split("\\|")[2].equals("0") ? region.split("\\|")[1] :
                    region.split("\\|")[2].replace("省", "");
        } catch (Exception e) {
            e.printStackTrace();
            return "未知";
        }
    }

    public static String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
