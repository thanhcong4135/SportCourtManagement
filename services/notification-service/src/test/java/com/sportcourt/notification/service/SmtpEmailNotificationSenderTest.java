package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationMessage;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailNotificationSenderTest {

    @Test
    void sendsUtf8MimeMessage() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        SmtpEmailNotificationSender sender = new SmtpEmailNotificationSender(
            mailSender,
            new NotificationTemplateRenderer("http://localhost:5173"),
            "no-reply@sportcourt.vn",
            "SportCourt"
        );

        sender.send(notification());

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("SportCourt - Đặt sân thành công");
        assertThat(mimeMessage.getAllRecipients()[0].toString()).isEqualTo("customer@example.com");
    }

    @Test
    void smtpFailureIsPropagatedForDispatcherRetry() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(MimeMessage.class));
        SmtpEmailNotificationSender sender = new SmtpEmailNotificationSender(
            mailSender,
            new NotificationTemplateRenderer("http://localhost:5173"),
            "no-reply@sportcourt.vn",
            "SportCourt"
        );

        assertThatThrownBy(() -> sender.send(notification()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("c***@example.com")
            .hasCauseInstanceOf(MailSendException.class);
    }

    private NotificationMessage notification() {
        NotificationMessage message = new NotificationMessage();
        message.setRecipient("customer@example.com");
        message.setTemplateCode("BOOKING_CONFIRMED");
        message.setTitle("Đặt sân thành công");
        message.setMessage("Booking của bạn đã được xác nhận.");
        message.setDeepLink("/account/bookings/booking-1");
        return message;
    }
}
