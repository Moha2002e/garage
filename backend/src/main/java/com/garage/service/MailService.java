package com.garage.service;

import com.garage.config.AppProperties;
import com.garage.domain.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties props;

    public MailService(JavaMailSender mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    public void sendPasswordReset(String to, Language language, String rawToken) {
        String link = props.frontUrl() + "/reset-password?token=" + rawToken;
        boolean fr = language == Language.FR;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.mailFrom());
        message.setTo(to);
        message.setSubject(fr ? "Réinitialisation de votre mot de passe" : "Reset your password");
        int minutes = props.resetTokenMinutes();
        message.setText(fr
                ? "Vous avez demandé la réinitialisation de votre mot de passe.\n\nCliquez sur ce lien (valable "
                + minutes + " minutes) :\n" + link
                + "\n\nSi vous n'êtes pas à l'origine de cette demande, ignorez ce message."
                : "You requested a password reset.\n\nUse this link (valid for " + minutes + " minutes):\n" + link
                + "\n\nIf you did not request this, ignore this message.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Never log the recipient or the link: both are sensitive.
            log.error("Failed to send password reset e-mail ({})", e.getClass().getSimpleName());
        }
    }
}
