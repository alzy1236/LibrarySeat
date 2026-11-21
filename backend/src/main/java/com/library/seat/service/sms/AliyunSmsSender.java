package com.library.seat.service.sms;

import com.library.seat.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * 阿里云短信发送器（dysmsapi，RPC 签名 V1）
 * <p>
 * 启用方式（后台「系统设置」或直接改 sys_config）：
 * <pre>
 *   sms.provider                 = aliyun
 *   sms.aliyun.accessKeyId       = LTAI...
 *   sms.aliyun.accessKeySecret   = xxxx
 *   sms.aliyun.signName          = 你的短信签名（需审核通过）
 *   sms.aliyun.templateCode      = SMS_123456789（模板变量名必须是 code）
 * </pre>
 * 模板内容示例：您的验证码为 ${code}，5 分钟内有效，请勿泄露给他人。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunSmsSender implements SmsSender {

    private static final String ENDPOINT = "https://dysmsapi.aliyuncs.com/";

    private final SysConfigService configService;

    @Override
    public boolean isMock() {
        return !"aliyun".equalsIgnoreCase(configService.getValue("sms.provider", "mock"));
    }

    @Override
    public boolean send(String phone, String code, String scene) {
        if (isMock()) {
            return false;
        }
        String ak = configService.getValue("sms.aliyun.accessKeyId", "");
        String sk = configService.getValue("sms.aliyun.accessKeySecret", "");
        String sign = configService.getValue("sms.aliyun.signName", "");
        String tpl = configService.getValue("sms.aliyun.templateCode", "");
        if (ak.isEmpty() || sk.isEmpty() || sign.isEmpty() || tpl.isEmpty()) {
            log.error("阿里云短信参数未配置完整，无法发送（accessKeyId/signName/templateCode 必填）");
            return false;
        }

        try {
            Map<String, String> params = new TreeMap<>();
            params.put("AccessKeyId", ak);
            params.put("Action", "SendSms");
            params.put("Format", "JSON");
            params.put("PhoneNumbers", phone);
            params.put("RegionId", "cn-hangzhou");
            params.put("SignName", sign);
            params.put("SignatureMethod", "HMAC-SHA1");
            params.put("SignatureNonce", String.valueOf(System.nanoTime()));
            params.put("SignatureVersion", "1.0");
            params.put("TemplateCode", tpl);
            params.put("TemplateParam", "{\"code\":\"" + code + "\"}");
            params.put("Timestamp", ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
            params.put("Version", "2017-05-25");

            // 构造待签名字符串
            StringBuilder canonical = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                canonical.append('&')
                        .append(percentEncode(e.getKey()))
                        .append('=')
                        .append(percentEncode(e.getValue()));
            }
            String stringToSign = "GET&" + percentEncode("/") + "&"
                    + percentEncode(canonical.substring(1));

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec((sk + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String signature = Base64.getEncoder()
                    .encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));

            String url = ENDPOINT + "?Signature=" + percentEncode(signature) + canonical;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int status = conn.getResponseCode();
            StringBuilder body = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    status >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    body.append(line);
                }
            }
            boolean ok = status == 200 && body.indexOf("\"Code\":\"OK\"") >= 0;
            if (ok) {
                log.info("阿里云短信发送成功：{} ({})", phone, scene);
            } else {
                log.error("阿里云短信发送失败：status={} body={}", status, body);
            }
            return ok;
        } catch (Exception e) {
            log.error("阿里云短信发送异常：{}", e.getMessage(), e);
            return false;
        }
    }

    /** 阿里云要求的特殊 URL 编码（空格 -> %20，* -> %2A，~ 不编码） */
    private String percentEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return value;
        }
    }
}