package com.iflytek.astron.console.hub.data;

import com.iflytek.astron.console.commons.entity.user.ChatUser;

import java.util.List;
import java.util.Optional;

public interface ChatUserDataService {

    /**
     * Query user by UID
     */
    Optional<ChatUser> findByUid(String uid);

    /**
     * Query user by mobile number
     */
    Optional<ChatUser> findByMobile(String mobile);

    /**
     * Query user by nickname
     */
    List<ChatUser> findByNickname(String nickname);

    /**
     * Query active users
     */
    List<ChatUser> findActiveUsers();

    /**
     * Create user
     */
    ChatUser createUser(ChatUser chatUser);

    /**
     * Update user information
     */
    ChatUser updateUser(ChatUser chatUser);

    /**
     * Activate/freeze user
     */
    boolean updateUserStatus(String uid, Integer status);

    /**
     * Count total users
     */
    long countUsers();

    /**
     * Query users with pagination
     */
    List<ChatUser> findUsersByPage(int page, int size);

    Optional<String> findNickNameByUid(String uid);
}
