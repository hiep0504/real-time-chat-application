package com.chatapp.chatapp.auth;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String toEmail, String otp) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(toEmail);
        message.setSubject("ChatApp - Email Verification");
        message.setText(
            "Hello,\n\n" +
            "Your verification code is: " + otp + "\n\n" +
            "This code will expire in 5 minutes.\n\n" +
            "Please do not share this code with anyone.\n\n" +
            "ChatApp Team"
        );

        mailSender.send(message);
    }

}
