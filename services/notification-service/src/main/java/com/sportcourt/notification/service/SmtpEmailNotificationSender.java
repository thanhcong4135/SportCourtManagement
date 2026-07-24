package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationChannel;
import com.sportcourt.notification.domain.NotificationMessage;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "notification.mail.enabled", havingValue = "true")
public class SmtpEmailNotificationSender implements NotificationChannelSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotificationSender.class);

    private final JavaMailSender mailSender;
    private final NotificationTemplateRenderer templateRenderer;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailNotificationSender(JavaMailSender mailSender,
                                       NotificationTemplateRenderer templateRenderer,
                                       @Value("${notification.mail.from:no-reply@sportcourt.vn}") String fromAddress,
                                       @Value("${notification.mail.from-name:SportCourt}") String fromName) {
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(NotificationMessage notificationMessage) {
        try {
            RenderedEmail rendered = templateRenderer.render(notificationMessage);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                true,
                StandardCharsets.UTF_8.name()
            );
            helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(notificationMessage.getRecipient());
            helper.setSubject(rendered.subject());
            helper.setText(rendered.textBody(), rendered.htmlBody());
            mailSender.send(mimeMessage);
            log.info("Email notification sent recipient={}", maskEmail(notificationMessage.getRecipient()));
        } catch (Exception ex) {
            throw new IllegalStateException(
                "SMTP delivery failed for " + maskEmail(notificationMessage.getRecipient()),
                ex
            );
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
