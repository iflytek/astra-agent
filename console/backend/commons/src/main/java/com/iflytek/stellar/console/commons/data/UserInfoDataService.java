package com.iflytek.astra.console.commons.data;


import com.iflytek.astra.console.commons.entity.user.UserInfo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserInfoDataService {

    /** Query user by UID */
    Optional<UserInfo> findByUid(String uid);

    /** Query user by username */
    Optional<UserInfo> findByUsername(String username);

    /** Query users by mobile number */
    List<UserInfo> findUsersByMobile(String mobile);

    /** Query users by username */
    List<UserInfo> findUsersByUsername(String username);

    /** Query users by a collection of mobile numbers */
    List<UserInfo> findUsersByMobiles(Collection<String> mobile);

    /** Fuzzy query users by nickname */
    List<UserInfo> findByNicknameLike(String nickname);

    /** Query users by account status */
    List<UserInfo> findByAccountStatus(Integer accountStatus);

    /** Query activated users */
    List<UserInfo> findActiveUsers();

    /**
     * Create user. The internal implementation of createOrGetUser uses a double-checked lock to ensure
     * creating a new user or returning the existing one.
     *
     * @param userInfo user information to create
     * @return UserInfo
     */
    UserInfo createOrGetUser(UserInfo userInfo);

    /** Delete user (logical deletion; update to frozen status) */
    boolean deleteUser(Long id);

    /** Update user account activation status */
    boolean updateAccountStatus(String uid, int accountStatus);

    /** Update user agreement consent status */
    boolean updateUserAgreement(String uid, int userAgreement);

    /** Batch query users by UID */
    List<UserInfo> findByUids(List<String> uids);

    /** Check whether username exists */
    boolean existsByUsername(String username);

    /** Check whether mobile number exists */
    boolean existsByMobile(String mobile);

    /** Check whether UID exists */
    boolean existsByUid(String uid);

    /** Count total users */
    long countUsers();

    /** Count users by account status */
    long countByAccountStatus(Integer accountStatus);

    /** Query users by page */
    List<UserInfo> findUsersByPage(int page, int size);

    /** Query users by conditions with pagination */
    List<UserInfo> findUsersByCondition(String username, String mobile, Integer accountStatus, int page, int size);

    /** Get current logged-in user info */
    UserInfo getCurrentUserInfo();

    /** Update user's basic information */
    UserInfo updateUserBasicInfo(String uid, String username, String nickname, String avatar, String mobile);

    /** Update current user's basic information */
    UserInfo updateCurrentUserBasicInfo(String nickname, String avatar);

    /** Current user agrees to user agreement */
    boolean agreeUserAgreement();

    /** Activate user account */
    boolean activateUser(String uid);

    /** Freeze user account */
    boolean freezeUser(String uid);

    /** Query users by time range */
    List<UserInfo> findUsersByTimeRange(java.time.LocalDateTime startTime, java.time.LocalDateTime endTime);

    /** Query recently registered users */
    List<UserInfo> findRecentUsers(int limit);

    Optional<String> findNickNameByUid(String uid);
}
