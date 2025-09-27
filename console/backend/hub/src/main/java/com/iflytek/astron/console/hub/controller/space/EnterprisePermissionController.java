package com.iflytek.astron.console.hub.controller.space;

import com.iflytek.astron.console.commons.service.space.EnterprisePermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * Enterprise team role permission configuration
 */
@Slf4j
@RestController
@RequestMapping("/enterprise-permission")
public class EnterprisePermissionController {

    @Resource
    private EnterprisePermissionService enterprisePermissionService;

}
