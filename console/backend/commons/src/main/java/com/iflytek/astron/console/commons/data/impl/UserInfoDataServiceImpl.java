package com.iflytek.astron.console.commons.data.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.data.UserInfoDataService;
import com.iflytek.astron.console.commons.entity.user.UserInfo;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.mapper.user.UserInfoMapper;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.util.I18nUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserInfoDataServiceImpl implements UserInfoDataService {

    private static final String[] CHINESE_ADJECTIVES = {
            "快乐的", "聪明的", "勇敢的", "温柔的", "活泼的", "阳光的", "可爱的", "优雅的",
            "神秘的", "幸运的", "开朗的", "善良的", "机智的", "热情的", "淡定的", "灵动的"
    };

    private static final String[] CHINESE_NOUNS = {
            "小猫", "小狗", "小鸟", "小鱼", "熊猫", "兔子", "狐狸", "松鼠",
            "星星", "月亮", "云朵", "花朵", "树叶", "彩虹", "蝴蝶", "小熊"
    };

    private static final String[] ENGLISH_ADJECTIVES = {
            "Happy", "Smart", "Brave", "Gentle", "Lively", "Sunny", "Cute", "Elegant",
            "Mysterious", "Lucky", "Cheerful", "Kind", "Clever", "Warm", "Cool", "Swift"
    };

    private static final String[] ENGLISH_NOUNS = {
            "Cat", "Dog", "Bird", "Fish", "Panda", "Rabbit", "Fox", "Squirrel",
            "Star", "Moon", "Cloud", "Flower", "Leaf", "Rainbow", "Butterfly", "Bear"
    };

    @Autowired
    private UserInfoMapper userInfoMapper;
    @Autowired
    private RedissonClient redissonClient;

    @Override
    public Optional<UserInfo> findByUid(String uid) {
        if (uid == null) {
            return Optional.empty();
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getUid, uid)
                .last("LIMIT 1");
        UserInfo userInfo = userInfoMapper.selectOne(wrapper);
        return Optional.ofNullable(userInfo);
    }

    @Override
    public Optional<UserInfo> findByUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return Optional.empty();
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getUsername, username)
                .last("LIMIT 1");
        UserInfo userInfo = userInfoMapper.selectOne(wrapper);
        return Optional.ofNullable(userInfo);
    }

    @Override
    public List<UserInfo> findUsersByMobile(String mobile) {
        if (StringUtils.isBlank(mobile)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getMobile, mobile);
        return userInfoMapper.selectList(wrapper);
    }

    @Override
    public List<UserInfo> findUsersByUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getUsername, username);
        return userInfoMapper.selectList(wrapper);
    }


    @Override
    public List<UserInfo> findUsersByMobiles(Collection<String> mobiles) {
        if (mobiles.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserInfo::getMobile, mobiles);
        return userInfoMapper.selectList(wrapper);
    }

    @Override
    public List<UserInfo> findByNicknameLike(String nickname) {
        if (StringUtils.isBlank(nickname)) {
            return List.of();
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(UserInfo::getNickname, nickname);
        return userInfoMapper.selectList(wrapper);
    }

    @Override
    public List<UserInfo> findByAccountStatus(Integer accountStatus) {
        if (accountStatus == null) {
            return List.of();
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getAccountStatus, accountStatus);
        return userInfoMapper.selectList(wrapper);
    }

    @Override
    public List<UserInfo> findActiveUsers() {
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getAccountStatus, 1);
        return userInfoMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfo createOrGetUser(UserInfo userInfo) {
        if (userInfo == null) {
            throw new IllegalArgumentException("User information cannot be null");
        }

        if (userInfo.getUid() == null) {
            throw new IllegalArgumentException("User UID cannot be null");
        }

        // First check: fail fast to avoid unnecessary lock contention
        Optional<UserInfo> existingUser = findByUid(userInfo.getUid());
        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        String lockKey = "user:create:uid:" + userInfo.getUid();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Attempt to acquire the lock: wait up to 5s, hold up to 10s
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);

            if (!acquired) {
                throw new IllegalStateException("Timed out acquiring distributed lock, please try again later");
            }

            try {
                // Second check: re-validate whether UID exists inside the lock
                Optional<UserInfo> existingUserInLock = findByUid(userInfo.getUid());
                if (existingUserInLock.isPresent()) {
                    return existingUserInLock.get();
                }

                // Set default values
                LocalDateTime now = LocalDateTime.now();
                if (userInfo.getCreateTime() == null) {
                    userInfo.setCreateTime(now);
                }
                if (userInfo.getUpdateTime() == null) {
                    userInfo.setUpdateTime(now);
                }
                if (userInfo.getDeleted() == null) {
                    userInfo.setDeleted(0);
                }
                if (StringUtils.isBlank(userInfo.getNickname())) {
                    userInfo.setNickname(generateRandomNickname());
                }
                userInfo.setId(null);

                userInfoMapper.insert(userInfo);
                log.info("Created new user: uid={}, username={}", userInfo.getUid(), userInfo.getUsername());
                return userInfo;

            } finally {
                // Release the lock
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring distributed lock", e);
        }
    }

    private String generateRandomNickname() {
        String language = I18nUtil.getLanguage();
        Random random = new Random();

        if ("zh".equals(language)) {
            String adjective = CHINESE_ADJECTIVES[random.nextInt(CHINESE_ADJECTIVES.length)];
            String noun = CHINESE_NOUNS[random.nextInt(CHINESE_NOUNS.length)];
            int number = random.nextInt(1000);
            return adjective + noun + number;
        } else {
            String adjective = ENGLISH_ADJECTIVES[random.nextInt(ENGLISH_ADJECTIVES.length)];
            String noun = ENGLISH_NOUNS[random.nextInt(ENGLISH_NOUNS.length)];
            int number = random.nextInt(1000);
            return adjective + noun + number;
        }
    }

    @Override
    public boolean deleteUser(Long id) {
        if (id == null) {
            return false;
        }
        // Use logical deletion; MyBatis Plus will automatically handle the @TableLogic annotation
        return userInfoMapper.deleteById(id) > 0;
    }

    @Override
    public boolean updateAccountStatus(String uid, int accountStatus) {
        if (uid == null) {
            return false;
        }
        LambdaUpdateWrapper<UserInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserInfo::getUid, uid)
                .set(UserInfo::getAccountStatus, accountStatus);
        return userInfoMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean updateUserAgreement(String uid, int userAgreement) {
        if (uid == null) {
            return false;
        }
        LambdaUpdateWrapper<UserInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserInfo::getUid, uid)
                .set(UserInfo::getUserAgreement, userAgreement);
        return userInfoMapper.update(null, wrapper) > 0;
    }

    @Override
    public List<UserInfo> findByUids(List<String> uids) {
        if (uids == null || uids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserInfo::getUid, uids);
        return userInfoMapper.selectList(wrapper);
    }

    @Override
    public boolean existsByUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return false;
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getUsername, username);
        return userInfoMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsByMobile(String mobile) {
        if (StringUtils.isBlank(mobile)) {
            return false;
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getMobile, mobile);
        return userInfoMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsByUid(String uid) {
        if (uid == null) {
            return false;
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getUid, uid);
        return userInfoMapper.selectCount(wrapper) > 0;
    }

    @Override
    public long countUsers() {
        return userInfoMapper.selectCount(null);
    }

    @Override
    public long countByAccountStatus(Integer accountStatus) {
        if (accountStatus == null) {
            return 0;
        }
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getAccountStatus, accountStatus);
        return userInfoMapper.selectCount(wrapper);
    }

    @Override
    public List<UserInfo> findUsersByPage(int page, int size) {
        if (page < 1 || size < 1) {
            return List.of();
        }
        Page<UserInfo> pageParam = new Page<>(page, size);
        Page<UserInfo> result = userInfoMapper.selectPage(
                pageParam,
                Wrappers.lambdaQuery(UserInfo.class)
                        .orderByDesc(UserInfo::getCreateTime));
        return result.getRecords();
    }

    @Override
    public List<UserInfo> findUsersByCondition(String username, String mobile, Integer accountStatus, int page, int size) {
        if (page < 1 || size < 1) {
            return List.of();
        }

        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(username)) {
            wrapper.like(UserInfo::getUsername, username);
        }

        if (StringUtils.isNotBlank(mobile)) {
            wrapper.like(UserInfo::getMobile, mobile);
        }

        if (accountStatus != null) {
            wrapper.eq(UserInfo::getAccountStatus, accountStatus);
        }

        wrapper.orderByDesc(UserInfo::getCreateTime);

        Page<UserInfo> pageParam = new Page<>(page, size);
        Page<UserInfo> result = userInfoMapper.selectPage(pageParam, wrapper);
        return result.getRecords();
    }

    @Override
    public UserInfo getCurrentUserInfo() {
        String currentUid = RequestContextUtil.getUID();
        return findByUid(currentUid).orElseThrow(() -> new BusinessException(ResponseEnum.DATA_NOT_FOUND, "Current user info does not exist"));
    }

    @Override
    public UserInfo updateUserBasicInfo(String uid, String username, String nickname, String avatar, String mobile) {
        if (uid == null) {
            throw new IllegalArgumentException("User UID cannot be null");
        }

        Optional<UserInfo> userInfoOpt = findByUid(uid);
        if (userInfoOpt.isEmpty()) {
            throw new BusinessException(ResponseEnum.DATA_NOT_FOUND);
        }

        UserInfo userInfo = userInfoOpt.get();

        if (StringUtils.isNotBlank(username)) {
            userInfo.setUsername(username);
        }
        if (StringUtils.isNotBlank(nickname)) {
            userInfo.setNickname(nickname);
        }
        if (StringUtils.isNotBlank(avatar)) {
            userInfo.setAvatar(avatar);
        }
        if (StringUtils.isNotBlank(mobile)) {
            userInfo.setMobile(mobile);
        }
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfoMapper.updateById(userInfo);
        return userInfo;
    }

    @Override
    public UserInfo updateCurrentUserBasicInfo(String nickname, String avatar) {
        String currentUid = RequestContextUtil.getUID();
        Optional<UserInfo> userInfoOpt = findByUid(currentUid);

        if (userInfoOpt.isEmpty()) {
            throw new IllegalArgumentException("Current user does not exist");
        }

        UserInfo userInfo = userInfoOpt.get();

        if (StringUtils.isNotBlank(nickname)) {
            userInfo.setNickname(nickname);
        }
        if (StringUtils.isNotBlank(avatar)) {
            userInfo.setAvatar(avatar);
        }
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfoMapper.updateById(userInfo);
        return userInfo;
    }

    @Override
    public boolean agreeUserAgreement() {
        String currentUid = RequestContextUtil.getUID();
        return updateUserAgreement(currentUid, 1);
    }

    @Override
    public boolean activateUser(String uid) {
        return updateAccountStatus(uid, 1);
    }

    @Override
    public boolean freezeUser(String uid) {
        return updateAccountStatus(uid, 2);
    }

    @Override
    public List<UserInfo> findUsersByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return List.of();
        }

        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(UserInfo::getCreateTime, startTime, endTime)
                .orderByDesc(UserInfo::getCreateTime);
        return userInfoMapper.selectList(wrapper);
    }

    @Override
    public List<UserInfo> findRecentUsers(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(UserInfo::getCreateTime)
                .last("LIMIT " + limit);
        return userInfoMapper.selectList(wrapper);
    }

    @Override
    public Optional<String> findNickNameByUid(String uid) {
        return Optional.ofNullable(uid)
                .map(u -> userInfoMapper.selectOne(
                        new LambdaQueryWrapper<UserInfo>()
                                .eq(UserInfo::getUid, u)
                                .last("LIMIT 1")))
                .map(UserInfo::getNickname);
    }

}
