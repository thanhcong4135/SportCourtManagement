package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class NotificationTemplateRenderer {

    private final String frontendBaseUrl;

    public NotificationTemplateRenderer(
        @Value("${notification.mail.frontend-base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    public RenderedEmail render(NotificationMessage message) {
        String title = nonBlank(message.getTitle(), "Thông báo từ SportCourt");
        String body = nonBlank(message.getMessage(), "Booking của bạn vừa được cập nhật.");
        String actionUrl = buildActionUrl(message.getDeepLink());
        String escapedTitle = HtmlUtils.htmlEscape(title);
        String escapedBody = HtmlUtils.htmlEscape(body);
        String escapedActionUrl = HtmlUtils.htmlEscape(actionUrl);

        String html = """
            <!doctype html>
            <html lang="vi">
              <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
              </head>
              <body style="margin:0;background:#f3f6f8;color:#17252f;font-family:Arial,sans-serif">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:24px 12px">
                  <tr>
                    <td align="center">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                             style="max-width:600px;background:#ffffff;border:1px solid #dfe6ec;border-radius:8px;overflow:hidden">
                        <tr>
                          <td style="padding:20px 24px;background:#0878ee;color:#ffffff;font-size:22px;font-weight:700">
                            SportCourt
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 24px">
                            <h1 style="margin:0 0 12px;font-size:24px;line-height:1.3">%s</h1>
                            <p style="margin:0 0 24px;color:#4f606b;font-size:16px;line-height:1.6">%s</p>
                            <a href="%s"
                               style="display:inline-block;padding:12px 18px;border-radius:6px;background:#0878ee;color:#ffffff;text-decoration:none;font-weight:700">
                              Xem chi tiết booking
                            </a>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 24px;border-top:1px solid #e7ecef;color:#778790;font-size:12px">
                            Đây là email tự động từ SportCourt.
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(escapedTitle, escapedTitle, escapedBody, escapedActionUrl);

        String text = title + System.lineSeparator()
            + System.lineSeparator()
            + body
            + System.lineSeparator()
            + System.lineSeparator()
            + "Xem chi tiết booking: " + actionUrl;

        return new RenderedEmail("SportCourt - " + title, html, text);
    }

    private String buildActionUrl(String deepLink) {
        if (deepLink == null || !deepLink.startsWith("/") || deepLink.startsWith("//") || deepLink.contains("://")) {
            return frontendBaseUrl;
        }
        return frontendBaseUrl + deepLink;
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "http://localhost:5173" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
