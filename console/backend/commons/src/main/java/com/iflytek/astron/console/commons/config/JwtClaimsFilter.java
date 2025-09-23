package com.iflytek.astron.console.commons.config;

import com.iflytek.astron.console.commons.data.UserInfoDataService;
import com.iflytek.astron.console.commons.dto.user.JwtInfoDto;
import com.iflytek.astron.console.commons.entity.user.UserInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtClaimsFilter extends OncePerRequestFilter {
    private final UserInfoDataService userInfoDataService;

    // Constant definitions
    public static final String USER_ID_ATTRIBUTE = "X-User-Id";
    public static final String USER_INFO_ATTRIBUTE = "X-User-Info";

    // User status constants
    private static final int DEFAULT_ACCOUNT_STATUS = 1;
    private static final int DEFAULT_USER_AGREEMENT = 0;
    private static final int DEFAULT_DELETED = 0;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            // Extract uid from JWT and set as request attribute
            String userId = jwt.getSubject();
            request.setAttribute(USER_ID_ATTRIBUTE, userId);

            // Extract complete user information
            String username = null;
            if (jwt.hasClaim("name")) {
                username = jwt.getClaim("name").toString();
            }
            String avatar = null;
            if (jwt.hasClaim("avatar")) {
                avatar = jwt.getClaim("avatar").toString();
            }
            String mobile = null;
            if (jwt.hasClaim("phone")) {
                mobile = jwt.getClaim("phone").toString();
            }
            JwtInfoDto jwtInfoDto = new JwtInfoDto(userId, username, avatar, mobile);
            UserInfo userInfo = createOrGetUserFromJwt(jwtInfoDto);

            // Set complete user information as request attribute
            request.setAttribute(USER_INFO_ATTRIBUTE, userInfo);
        }

        // Pass the request to the next filter in the chain
        filterChain.doFilter(request, response);
    }

    private UserInfo createOrGetUserFromJwt(JwtInfoDto jwtInfoDto) {
        // Add null checks
        if (jwtInfoDto == null || jwtInfoDto.uid() == null) {
            throw new IllegalArgumentException("JWT info or user ID cannot be null");
        }

        Optional<UserInfo> existUserInfo = userInfoDataService.findByUid(jwtInfoDto.uid());
        if (existUserInfo.isPresent()) {
            return existUserInfo.get();
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUid(jwtInfoDto.uid());
        userInfo.setUsername(jwtInfoDto.username());
        userInfo.setAvatar(jwtInfoDto.avatar());
        userInfo.setMobile(jwtInfoDto.mobile());
        userInfo.setAccountStatus(DEFAULT_ACCOUNT_STATUS);
        userInfo.setUserAgreement(DEFAULT_USER_AGREEMENT);
        userInfo.setCreateTime(LocalDateTime.now());
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfo.setDeleted(DEFAULT_DELETED);

        // DistributedLock ensures creating a new user or returning the existing user info
        return userInfoDataService.createOrGetUser(userInfo);
    }
}
