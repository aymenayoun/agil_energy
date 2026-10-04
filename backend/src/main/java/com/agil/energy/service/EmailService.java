package com.agil.energy.service;

import java.util.Map;

public interface EmailService {

    /**
     * Sends an HTML email rendered from a Thymeleaf template under classpath:/templates/.
     *
     * @param to            recipient email
     * @param subject       email subject
     * @param templateName  template name without the .html suffix (e.g. "otp-email")
     * @param variables     model variables passed to the template
     */
    void sendHtml(String to, String subject, String templateName, Map<String, Object> variables);
}