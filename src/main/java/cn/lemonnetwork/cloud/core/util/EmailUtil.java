//为防止有人拿本喵邮箱干坏事，本java上传后将不会更新
//如果你想使用你自己的邮箱，可对变量定义进行修改喵

package cn.lemonnetwork.cloud.core.util;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

public class EmailUtil {
    private static String host = "smtp.qq.com"; //自行填写喵
    private static String port = "587"; //自行填写喵
    private static String username = "duduskz@qq.com"; //自行填写喵
    private static String password = "zebwufdskeopfchc"; //自行填写喵
    public static String admin = "3949047161@qq.com"; //自行填写喵

    private static Session session;

    public static void init() {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    public static boolean sendEmail(String recipient, String subject, String content) {


        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject(subject);
            message.setText(content);

            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        }
    }
}
