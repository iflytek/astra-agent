package com.iflytek.astron.console.toolkit.service.group;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.toolkit.entity.table.group.GroupVisibility;
import com.iflytek.astron.console.toolkit.entity.vo.group.GroupUserTagVO;
import com.iflytek.astron.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astron.console.toolkit.mapper.group.GroupVisibilityMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing group visibility permissions and access control Handles repository, tool,
 * and resource visibility settings for users and groups
 */
@Service
@Slf4j
public class GroupVisibilityService extends ServiceImpl<GroupVisibilityMapper, GroupVisibility> {

    /**
     * Get a single GroupVisibility record with limit 1
     *
     * @param wrapper Query conditions wrapper
     * @return Single GroupVisibility entity or null if not found
     */
    public GroupVisibility getOnly(QueryWrapper<GroupVisibility> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    @Resource
    private GroupVisibilityMapper groupVisibilityMapper;

    /**
     * Set repository visibility permissions for specified users Manages which users can access a
     * repository based on visibility settings
     *
     * @param id The repository ID to set visibility for
     * @param type The type of resource (repository, tool, etc.)
     * @param visibility Visibility level: 0=private (only self), 1=group visible, etc.
     * @param uids List of user IDs who should have access
     */
    public void setRepoVisibility(Long id, Integer type, Integer visibility, List<String> uids) {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (visibility == 0) {// Only visible to self
            return;
        }
        // Remove existing associations
        if (spaceId != null) {
            this.remove(Wrappers.lambdaQuery(GroupVisibility.class).eq(GroupVisibility::getSpaceId, spaceId).eq(GroupVisibility::getType, type).eq(GroupVisibility::getRelationId, id.toString()));
        } else {
            this.remove(Wrappers.lambdaQuery(GroupVisibility.class).eq(GroupVisibility::getUid, UserInfoManagerHandler.getUserId()).eq(GroupVisibility::getType, type).eq(GroupVisibility::getRelationId, id.toString()));
        }
        if (CollectionUtils.isEmpty(uids)) {// Available uids is empty
            return;
        }
        // Add new associations
        List<GroupVisibility> groupVisibilityList = new ArrayList<>();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        for (String uid : uids) {
            GroupVisibility groupVisibility = new GroupVisibility();
            groupVisibilityList.add(groupVisibility);
            groupVisibility.setUid(UserInfoManagerHandler.getUserId());
            groupVisibility.setType(type);
            groupVisibility.setUserId(uid);
            groupVisibility.setRelationId(id.toString());
            groupVisibility.setCreateTime(timestamp);
            if (spaceId != null) {
                groupVisibility.setSpaceId(spaceId);
            }
        }
        this.saveBatch(groupVisibilityList);
    }


    /**
     * List users who have access to a specific resource Returns user information and tag data for group
     * visibility management
     *
     * @param type The type of resource (repository, tool, etc.)
     * @param id The resource ID to query access for
     * @return List of GroupUserTagVO containing user information and tags
     */
    public List<GroupUserTagVO> listUser(Long type, Long id) {
        return groupVisibilityMapper.listUser(UserInfoManagerHandler.getUserId(), type, id);
    }


    /**
     * Get repository visibility list for the current user Returns repositories that the current user
     * has visibility access to
     *
     * @return List of GroupVisibility entities for repositories
     */
    public List<GroupVisibility> getRepoVisibilityList() {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        return groupVisibilityMapper.getRepoVisibilityList(UserInfoManagerHandler.getUserId(), spaceId);
    }


    /**
     * Get tool visibility list for the current user Returns tools that the current user has visibility
     * access to
     *
     * @return List of GroupVisibility entities for tools
     */
    public List<GroupVisibility> getToolVisibilityList() {
        return groupVisibilityMapper.getToolVisibilityList(UserInfoManagerHandler.getUserId());
    }

    /**
     * Get square tool visibility list for the current user Returns tools from the public square that
     * the current user has access to
     *
     * @return List of GroupVisibility entities for square tools
     */
    public List<GroupVisibility> getSquareToolVisibilityList() {
        return groupVisibilityMapper.getSquareToolVisibilityList(UserInfoManagerHandler.getUserId());
    }
}
