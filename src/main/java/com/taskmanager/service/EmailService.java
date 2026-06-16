package com.taskmanager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final RestTemplate restTemplate;

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    @Value("${brevo.from-email:}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        try {
            System.out.println("[EmailService] Gửi email xác nhận tới: " + toEmail);

            if (brevoApiKey == null || brevoApiKey.isEmpty()) {
                System.err.println("[EmailService] BREVO_API_KEY chưa được cấu hình!");
                return;
            }

            String verifyLink = frontendUrl + "/verify-email?token=" + token;
            String htmlContent = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:32px;background:#f4f5f8;border-radius:16px">
                  <div style="text-align:center;margin-bottom:24px">
                    <span style="font-size:28px;font-weight:800;color:#2563eb">TaskFlow</span>
                  </div>
                  <div style="background:#fff;border-radius:12px;padding:28px">
                    <h2 style="margin:0 0 12px;font-size:20px;color:#13152b">Xác nhận địa chỉ email</h2>
                    <p style="color:#6b7089;font-size:14px;line-height:1.6">
                      Cảm ơn bạn đã đăng ký TaskFlow! Nhấn vào nút bên dưới để xác nhận email và kích hoạt tài khoản.
                    </p>
                    <div style="text-align:center;margin:28px 0">
                      <a href="%s" style="background:linear-gradient(135deg,#3b82f6,#2563eb);color:#fff;padding:14px 32px;border-radius:10px;text-decoration:none;font-weight:700;font-size:15px">
                        Xác nhận email
                      </a>
                    </div>
                    <p style="color:#8a8fa3;font-size:12px;text-align:center">
                      Link có hiệu lực trong 24 giờ. Nếu bạn không đăng ký, hãy bỏ qua email này.
                    </p>
                  </div>
                </div>
                """.formatted(verifyLink);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> body = Map.of(
                "sender", Map.of("name", "TaskFlow", "email", fromEmail),
                "to", List.of(Map.of("email", toEmail)),
                "subject", "TaskFlow — Xác nhận email của bạn",
                "htmlContent", htmlContent
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email", request, String.class);
            System.out.println("[EmailService] Gửi email xác nhận: " + response.getStatusCode());
        } catch (Exception e) {
            System.err.println("[EmailService] Lỗi gửi email xác nhận: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        try {
            System.out.println("[EmailService] Bắt đầu gửi email reset password qua Brevo API tới: " + toEmail);
            
            if (brevoApiKey == null || brevoApiKey.isEmpty()) {
                System.err.println("[EmailService] BREVO_API_KEY chưa được cấu hình!");
                return;
            }

            String resetLink = frontendUrl + "/reset-password?token=" + token;
            String htmlContent = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:32px;background:#f4f5f8;border-radius:16px">
                  <div style="text-align:center;margin-bottom:24px">
                    <span style="font-size:28px;font-weight:800;color:#2563eb">TaskFlow</span>
                  </div>
                  <div style="background:#fff;border-radius:12px;padding:28px">
                    <h2 style="margin:0 0 12px;font-size:20px;color:#13152b">Đặt lại mật khẩu</h2>
                    <p style="color:#6b7089;font-size:14px;line-height:1.6">
                      Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.
                      Nhấn vào nút bên dưới để tạo mật khẩu mới.
                    </p>
                    <div style="text-align:center;margin:28px 0">
                      <a href="%s" style="background:linear-gradient(135deg,#3b82f6,#2563eb);color:#fff;padding:14px 32px;border-radius:10px;text-decoration:none;font-weight:700;font-size:15px">
                        Đặt lại mật khẩu
                      </a>
                    </div>
                    <p style="color:#8a8fa3;font-size:12px;text-align:center">
                      Link có hiệu lực trong 15 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.
                    </p>
                  </div>
                </div>
                """.formatted(resetLink);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> sender = Map.of(
                "name", "TaskFlow",
                "email", fromEmail
            );
            
            Map<String, Object> recipient = Map.of(
                "email", toEmail
            );

            Map<String, Object> body = Map.of(
                "sender", sender,
                "to", List.of(recipient),
                "subject", "TaskFlow — Đặt lại mật khẩu",
                "htmlContent", htmlContent
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email", request, String.class);
            
            System.out.println("[EmailService] Kết quả gửi mail từ Brevo: " + response.getStatusCode() + " - " + response.getBody());
        } catch (Exception e) {
            System.err.println("[EmailService] Lỗi khi gửi email tới " + toEmail + " qua Brevo: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
