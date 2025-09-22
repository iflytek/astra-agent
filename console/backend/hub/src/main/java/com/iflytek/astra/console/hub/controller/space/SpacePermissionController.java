package com.iflytek.astra.console.hub.controller.space;

import com.iflytek.astra.console.commons.service.space.SpacePermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * Space role permission configuration
 */
@Slf4j
@RestController
@RequestMapping("/space-permission")
public class SpacePermissionController {

    @Resource
    private SpacePermissionService spacePermissionService;

}
