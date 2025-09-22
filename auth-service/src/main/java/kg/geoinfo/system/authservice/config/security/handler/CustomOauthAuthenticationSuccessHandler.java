package kg.geoinfo.system.authservice.config.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kg.geoinfo.system.authservice.dao.type.UserEventType;
import kg.geoinfo.system.authservice.service.UserEventService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;

public class CustomOauthAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserEventService eventService;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public CustomOauthAuthenticationSuccessHandler(
        String authSuccessUrl,
        UserEventService eventService
    ) {
        this.eventService = eventService;
        this.setDefaultTargetUrl(authSuccessUrl);
        this.setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws ServletException, IOException {
        super.onAuthenticationSuccess(request, response, authentication);
        SavedRequest savedRequest = this.requestCache.getRequest(request, response);
        if (savedRequest != null) {
            this.requestCache.removeRequest(request, response);
        }

        String clientId = HandlerUtils.getClientId(savedRequest);
        eventService.createEvent(UserEventType.USER_LOGIN, clientId, request);
    }
}
