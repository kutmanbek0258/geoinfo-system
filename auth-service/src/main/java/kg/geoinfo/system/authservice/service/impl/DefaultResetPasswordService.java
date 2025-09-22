package kg.geoinfo.system.authservice.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kg.geoinfo.system.authservice.components.ConfirmationStore;
import kg.geoinfo.system.authservice.components.OTPStore;
import kg.geoinfo.system.authservice.config.security.properties.AuthorizationServerProperties;
import kg.geoinfo.system.authservice.dao.entity.UserEntity;
import kg.geoinfo.system.authservice.exception.InformationException;
import kg.geoinfo.system.authservice.exception.ResetPasswordException;
import kg.geoinfo.system.authservice.service.MessageService;
import kg.geoinfo.system.authservice.service.ResetPasswordService;
import kg.geoinfo.system.authservice.service.UserService;
import kg.geoinfo.system.authservice.utils.CryptoUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class DefaultResetPasswordService implements ResetPasswordService {

    private static final String SESSION_ID_HEADER = "reset-password-session";

    private final OTPStore otpStore;
    private final ConfirmationStore resetPasswordStore;
    private final AuthorizationServerProperties authorizationServerProperties;
    private final UserService userService;
    private final MessageService messageService;

    public DefaultResetPasswordService(OTPStore otpStore,
                                       @Qualifier("resetPasswordStore")ConfirmationStore resetPasswordStore,
                                       AuthorizationServerProperties authorizationServerProperties,
                                       UserService userService,
                                       MessageService messageService) {
        this.otpStore = otpStore;
        this.resetPasswordStore = resetPasswordStore;
        this.authorizationServerProperties = authorizationServerProperties;
        this.userService = userService;
        this.messageService = messageService;
    }

    @Override
    public void initial(String email, HttpServletResponse response) {
        email = email.trim().toLowerCase();
        if (!userService.existByEmail(email)) {
            throw InformationException.builder("$email.not.found").build();
        }

        OTPStore.GenerationResult generationResult = otpStore.generate(response);
        try {
            resetPasswordStore.save(
                new ConfirmationStore.StoreItem(email, generationResult.otp(), null),
                generationResult.sessionId()
            );
        } catch (Exception e) {
            throw InformationException.builder("$happened.unexpected.error").build();
        }
        UserEntity user = userService.findByEmail(email);
//        mailSenderService.sendNewMail(
//            email,
//            messageService.getMessage("email.subject.init.reset.password"),
//            ImmutableMap.<String, Object>builder()
//                .put("firstName", user.getFirstName())
//                .put("otp", generationResult.otp())
//                .build()
//                .toString()
//        );
    }

    @Override
    public void confirmEmail(String otp, HttpServletRequest request) {
        otp = otp.trim();
        if (!otpStore.validate(otp, request)) {
            throw new ResetPasswordException("$opt.incorrect");
        }

        // по идентификатору по OTPStore получаем данные из resetPasswordStore. Там находиться email пользователя.
        // Он был сохранён на первом шаге.
        String sessionId = otpStore.getSessionId(request);
        ConfirmationStore.StoreItem storeItem;
        try {
            storeItem = resetPasswordStore.take(sessionId);
        } catch (Exception e) {
            throw InformationException.builder("$happened.unexpected.error").build();
        }

        // Генерируем специальный идентификатор сессии, который укажем в email сообщении.
        String resetPasswordSessionId = CryptoUtils.hash(sessionId + "-" + otp);
        try {
            resetPasswordStore.save(storeItem, resetPasswordSessionId);
        } catch (Exception e) {
            throw InformationException.builder("$happened.unexpected.error").build();
        }

        // Находим пользователя по email в БД. Он нам нужен, для того чтобы добавить в сообщение имя пользователя.
        UserEntity user = userService.findByEmail(storeItem.email());

        // отправляем email сообщение.
//        mailSenderService.sendNewMail(
//            storeItem.email(),
//            messageService.getMessage("email.subject.reset.password"),
//            ImmutableMap.<String, Object>builder()
//                .put("firstName", user.getFirstName())
//                .put("resetPasswordUrl", this.getResetPasswordUrl(resetPasswordSessionId))
//                .build()
//                .toString()
//        );
    }

    /**
     * Генерация URL на форму сброса пароля
     *
     * @param sessionId специальный идентификатор сессии
     */
    private String getResetPasswordUrl(String sessionId) {
        String httpUrl = authorizationServerProperties.getIssuerUrl()
            + authorizationServerProperties.getResetPasswordEndpoint();
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(httpUrl);
        builder.queryParam("resetSessionId", sessionId);
        return builder.build().toUriString();
    }

    @Override
    public void setNewPassword(String newPassword, HttpServletRequest request) {
        // Проверяем существует ли в запросе специальный заголовок
        if (request.getHeader(SESSION_ID_HEADER) == null) {
            throw new ResetPasswordException("$reset.password.broke");
        }

        // Пытаемся получить значение специального заголовка.
        String resetPasswordSessionId = request.getHeader(SESSION_ID_HEADER);

        // Пытаемся получить данные из resetPasswordStore по значению из заголовка
        ConfirmationStore.StoreItem storeItem;
        try {
            storeItem = resetPasswordStore.take(resetPasswordSessionId);
        } catch (Exception e) {
            throw InformationException.builder("$happened.unexpected.error").build();
        }

        // Если данных нет, то выбрасываем ошибку
        if (storeItem == null) {
            throw new ResetPasswordException("$reset.password.broke");
        }

        // Если данные есть, то меняем пароль у пользователя. Email берём тот который получили из resetPasswordStore
        userService.changePassword(storeItem.email(), newPassword);
    }
}
