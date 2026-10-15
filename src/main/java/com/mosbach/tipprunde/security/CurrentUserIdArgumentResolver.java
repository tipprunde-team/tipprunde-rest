package com.mosbach.tipprunde.security;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.mosbach.tipprunde.error.ApiException;

// Gibt @CurrentUserId-Parametern die Nutzer-ID, die der AuthInterceptor abgelegt hat
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && (type == long.class || type == Long.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object userId = webRequest.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (userId == null) {
            // Der Endpunkt ist geschützt, es muss also eine ID da sein: sonst lieber ablehnen
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Bitte melde dich erneut an.");
        }
        return userId;
    }
}
