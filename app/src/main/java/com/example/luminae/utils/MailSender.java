package com.example.luminae.utils;

import android.os.AsyncTask;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

/**
 * Sends email via SMTP on a background thread using JavaMail.
 *
 * Add to build.gradle (app):
 *   implementation 'com.sun.mail:android-mail:1.6.7'
 *   implementation 'com.sun.mail:android-activation:1.6.7'
 */
public class MailSender {

    // ── Configure your sender credentials here ────────────────────────────────
    // Use a dedicated "app" Gmail account — never the admin's personal account.
    // In Gmail: Settings → Security → App Passwords → generate one for "Mail".
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String FROM_EMAIL = "luminae.noreply@gmail.com"; // your sender Gmail
    private static final String FROM_PASS  = "xxxx xxxx xxxx xxxx";       // Gmail App Password

    public interface Callback {
        void onSuccess();
        void onFailure(Exception e);
    }

    /**
     * Sends a welcome email to a newly registered student.
     *
     * @param toEmail   student's institutional email
     * @param fullName  student's full name
     * @param username  auto-generated username (email prefix)
     * @param password  auto-generated random password
     * @param callback  result callback (runs on background thread — post to UI if needed)
     */
    public static void sendWelcomeEmail(String toEmail, String fullName,
                                        String username, String password,
                                        Callback callback) {
        AsyncTask.execute(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.auth",            "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host",            SMTP_HOST);
                props.put("mail.smtp.port",            SMTP_PORT);

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(FROM_EMAIL, FROM_PASS);
                    }
                });

                String firstName = fullName.contains(" ")
                        ? fullName.substring(0, fullName.indexOf(' '))
                        : fullName;

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(FROM_EMAIL, "Lumina"));
                message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
                message.setSubject("Welcome to Luminae – Your Account Details");
                message.setContent(buildHtml(firstName, toEmail, username, password), "text/html; charset=utf-8");

                Transport.send(message);
                if (callback != null) callback.onSuccess();

            } catch (Exception e) {
                if (callback != null) callback.onFailure(e);
            }
        });
    }

    // ── HTML email template ───────────────────────────────────────────────────
    private static String buildHtml(String firstName, String email,
                                    String username, String password) {
        return "<!DOCTYPE html><html><body style=\"font-family:'Segoe UI',Arial,sans-serif;"
                + "background:#f5f5f5;margin:0;padding:0;\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td align=\"center\" style=\"padding:40px 0;\">"
                + "<table width=\"520\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;"
                + "border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.08);overflow:hidden;\">"

                // Header
                + "<tr><td style=\"background:#6B0A0A;padding:32px 40px;text-align:center;\">"
                + "<h1 style=\"color:#ffffff;margin:0;font-size:26px;font-weight:700;letter-spacing:1px;\">LUMINAE</h1>"
                + "<p style=\"color:#f0c040;margin:4px 0 0;font-size:12px;letter-spacing:2px;\">LEARNING PORTAL</p>"
                + "</td></tr>"

                // Body
                + "<tr><td style=\"padding:36px 40px;\">"
                + "<p style=\"font-size:16px;color:#333;margin:0 0 8px;\">Hi <strong>" + firstName + "</strong>,</p>"
                + "<p style=\"font-size:14px;color:#555;line-height:1.7;margin:0 0 24px;\">"
                + "Welcome to <strong>Luminae</strong>! Your student account has been created by your administrator. "
                + "Use the credentials below to log in.</p>"

                // Credentials box
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fdf4e7;"
                + "border:1px solid #f0c040;border-radius:8px;margin-bottom:24px;\">"
                + "<tr><td style=\"padding:20px 24px;\">"
                + "<p style=\"margin:0 0 12px;font-size:11px;color:#888;text-transform:uppercase;"
                + "letter-spacing:1px;font-weight:600;\">Your Login Credentials</p>"
                + "<table cellpadding=\"4\" cellspacing=\"0\">"
                + "<tr><td style=\"font-size:13px;color:#666;font-weight:600;padding-right:16px;\">Email</td>"
                + "<td style=\"font-size:13px;color:#222;font-family:monospace;\">" + email + "</td></tr>"
                + "<tr><td style=\"font-size:13px;color:#666;font-weight:600;padding-right:16px;\">Username</td>"
                + "<td style=\"font-size:13px;color:#222;font-family:monospace;\">" + username + "</td></tr>"
                + "<tr><td style=\"font-size:13px;color:#666;font-weight:600;padding-right:16px;\">Password</td>"
                + "<td style=\"font-size:20px;color:#6B0A0A;font-family:monospace;font-weight:700;"
                + "letter-spacing:3px;\">" + password + "</td></tr>"
                + "</table></td></tr></table>"

                + "<p style=\"font-size:12px;color:#e53935;margin:0 0 20px;\">"
                + "⚠️ Please change your password after your first login.</p>"
                + "<p style=\"font-size:12px;color:#999;margin:0;\">"
                + "If you did not expect this email, contact your administrator immediately.</p>"
                + "</td></tr>"

                // Footer
                + "<tr><td style=\"background:#f9f9f9;padding:16px 40px;border-top:1px solid #eee;"
                + "text-align:center;\">"
                + "<p style=\"font-size:11px;color:#bbb;margin:0;\">© Luminae · Bulacan State University</p>"
                + "</td></tr>"

                + "</table></td></tr></table></body></html>";
    }
}