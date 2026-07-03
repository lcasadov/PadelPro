package com.padelpro.notificaciones.infrastructure.email;

import com.padelpro.notificaciones.domain.model.WelcomeEmail;

/**
 * Builds the subject and body of the welcome email (change {@code usuarios-alta-edicion-email},
 * task 1.5). Two variants per D3:
 * <ul>
 *   <li><b>with password</b> (direct creation): includes the temporary password and asks the user
 *       to change it on first login;</li>
 *   <li><b>account approved</b> (approval): welcome message, no password.</li>
 * </ul>
 *
 * <p>Plain-text bodies keep the adapter simple and provider-agnostic (Ethereal, SES, ...).
 */
final class WelcomeEmailTemplate {

    private static final String SUBJECT = "Bienvenido/a a PadelPro";

    private WelcomeEmailTemplate() {
    }

    static String subject() {
        return SUBJECT;
    }

    static String body(WelcomeEmail email) {
        String name = (email.recipientName() != null && !email.recipientName().isBlank())
                ? email.recipientName()
                : "";
        String greeting = name.isBlank() ? "Hola," : "Hola " + name + ",";

        if (email.hasPassword()) {
            return greeting + "\n\n"
                    + "Se ha creado tu cuenta en PadelPro y ya está activa.\n\n"
                    + "Puedes entrar con tu email (" + email.recipientEmail() + ") y esta "
                    + "contraseña temporal:\n\n"
                    + "    " + email.temporaryPassword() + "\n\n"
                    + "Por seguridad, se te pedirá cambiarla la primera vez que inicies sesión.\n\n"
                    + "Un saludo,\nEl equipo de PadelPro";
        }

        return greeting + "\n\n"
                + "Tu cuenta en PadelPro ha sido aprobada y ya está activa.\n\n"
                + "Puedes iniciar sesión con tu email (" + email.recipientEmail() + ") y la "
                + "contraseña que elegiste al registrarte.\n\n"
                + "Un saludo,\nEl equipo de PadelPro";
    }
}
