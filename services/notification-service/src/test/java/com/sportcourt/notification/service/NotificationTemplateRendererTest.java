package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateRendererTest {

    @Test
    void renderEscapesDynamicContentAndBuildsInternalCta() {
        NotificationTemplateRenderer renderer = new NotificationTemplateRenderer("https://sportcourt.test/");
        NotificationMessage message = new NotificationMessage();
        message.setTitle("<script>alert('title')</script>");
        message.setMessage("<img src=x onerror=alert(1)>");
        message.setDeepLink("/account/bookings/booking-1");

        RenderedEmail rendered = renderer.render(message);

        assertThat(rendered.htmlBody())
            .contains("&lt;script&gt;", "&lt;img", "https://sportcourt.test/account/bookings/booking-1")
            .doesNotContain("<script>", "<img src=x");
        assertThat(rendered.textBody()).contains("https://sportcourt.test/account/bookings/booking-1");
    }

    @Test
    void externalDeepLinkFallsBackToFrontendRoot() {
        NotificationTemplateRenderer renderer = new NotificationTemplateRenderer("https://sportcourt.test");
        NotificationMessage message = new NotificationMessage();
        message.setTitle("Title");
        message.setMessage("Message");
        message.setDeepLink("//attacker.test/path");

        assertThat(renderer.render(message).textBody())
            .contains("https://sportcourt.test")
            .doesNotContain("attacker.test");
    }
}
