package com.iflytek.astra.console.commons.aspect.space;

import com.iflytek.astra.console.commons.annotation.space.EnterprisePreAuth;
import com.iflytek.astra.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astra.console.commons.entity.space.EnterprisePermission;
import com.iflytek.astra.console.commons.entity.space.SpacePermission;
import com.iflytek.astra.console.commons.service.space.EnterprisePermissionService;
import com.iflytek.astra.console.commons.service.space.SpacePermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PermissionValidator implements ApplicationListener<ContextRefreshedEvent> {
    @Autowired
    private EnterprisePermissionService enterprisePermissionService;
    @Autowired
    private SpacePermissionService spacePermissionService;
    @Autowired
    private ApplicationContext applicationContext;

    private static final boolean isInit = false;


    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        validateSpacePermission();
        validateEnterprisePermission();
    }

    private void validateSpacePermission() {
        List<Method> methodList = getMethodsWithAnnotation(SpacePreAuth.class);
        validateSpacePermissionKeys(methodList);
        processSpacePermissions(methodList);
    }

    private void validateSpacePermissionKeys(List<Method> methodList) {
        for (Method method : methodList) {
            RequestMapping mapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
            if (mapping != null && mapping.method().length > 0) {
                String httpMethod = mapping.method()[0].name();
                String methodName = method.getName();
                String simpleName = method.getDeclaringClass().getSimpleName();
                String format = String.format("%s_%s_%s", simpleName, methodName, httpMethod);
                if (!Objects.equals(format, method.getAnnotation(SpacePreAuth.class).key())) {
                    log.warn("Space permission key {} is not standard; suggested: {}", method.getAnnotation(SpacePreAuth.class).key(), format);
                }
            }
        }
    }

    private void processSpacePermissions(List<Method> methodList) {
        Map<String, List<Method>> methodMap = methodList.stream()
                .collect(Collectors.groupingBy(method -> method.getAnnotation(SpacePreAuth.class).key(), Collectors.toList()));
        Set<String> keys = methodMap.keySet();
        if (!keys.isEmpty()) {
            List<String> dbKeys = spacePermissionService.listByKeys(keys);
            if (dbKeys.size() != keys.size()) {
                handleMissingSpacePermissions(keys, dbKeys, methodMap);
            }
        }
    }

    private void handleMissingSpacePermissions(Set<String> keys, List<String> dbKeys, Map<String, List<Method>> methodMap) {
        if (isInit) {
            insertMissingSpacePermissions(keys, dbKeys, methodMap);
        } else {
            throwSpacePermissionError(keys, dbKeys);
        }
    }

    private void insertMissingSpacePermissions(Set<String> keys, List<String> dbKeys, Map<String, List<Method>> methodMap) {
        dbKeys.forEach(keys::remove);
        List<SpacePermission> spacePermissions = new ArrayList<>(100);
        for (Map.Entry<String, List<Method>> entry : methodMap.entrySet()) {
            String key = entry.getKey();
            if (!dbKeys.contains(key)) {
                SpacePreAuth annotation = entry.getValue().get(0).getAnnotation(SpacePreAuth.class);
                spacePermissions.add(SpacePermission.builder()
                        .permissionKey(key)
                        .module(annotation.module())
                        .point(annotation.point())
                        .description(annotation.description())
                        .owner(true)
                        .admin(true)
                        .member(true)
                        .availableExpired(false)
                        .build());
            }
        }
        spacePermissionService.insertBatch(spacePermissions);
    }

    private void throwSpacePermissionError(Set<String> keys, List<String> dbKeys) {
        StringBuilder errMsg = new StringBuilder();
        for (String key : keys) {
            if (!dbKeys.contains(key)) {
                errMsg.append(key).append("\n");
            }
        }
        throw new IllegalStateException("Space permission misconfiguration. Table agent_space_permission is missing keys:\n" + errMsg);
    }

    private void validateEnterprisePermission() {
        List<Method> methodList = getMethodsWithAnnotation(EnterprisePreAuth.class);
        validateEnterprisePermissionKeys(methodList);
        processEnterprisePermissions(methodList);
    }

    private void validateEnterprisePermissionKeys(List<Method> methodList) {
        for (Method method : methodList) {
            RequestMapping mapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
            if (mapping != null && mapping.method().length > 0) {
                String httpMethod = mapping.method()[0].name();
                String methodName = method.getName();
                String simpleName = method.getDeclaringClass().getSimpleName();
                String format = String.format("%s_%s_%s", simpleName, methodName, httpMethod);
                if (!Objects.equals(format, method.getAnnotation(EnterprisePreAuth.class).key())) {
                    log.warn("Enterprise permission key {} is not standard; suggested: {}", method.getAnnotation(EnterprisePreAuth.class).key(), format);
                }
            }
        }
    }

    private void processEnterprisePermissions(List<Method> methodList) {
        Map<String, List<Method>> methodMap = methodList.stream()
                .collect(Collectors.groupingBy(method -> method.getAnnotation(EnterprisePreAuth.class).key(), Collectors.toList()));
        Set<String> keys = methodMap.keySet();
        if (!keys.isEmpty()) {
            List<String> dbKeys = enterprisePermissionService.listByKeys(keys);
            if (dbKeys.size() != keys.size()) {
                handleMissingEnterprisePermissions(keys, dbKeys, methodMap);
            }
        }
    }

    private void handleMissingEnterprisePermissions(Set<String> keys, List<String> dbKeys, Map<String, List<Method>> methodMap) {
        if (isInit) {
            insertMissingEnterprisePermissions(keys, dbKeys, methodMap);
        } else {
            throwEnterprisePermissionError(keys, dbKeys);
        }
    }

    private void insertMissingEnterprisePermissions(Set<String> keys, List<String> dbKeys, Map<String, List<Method>> methodMap) {
        dbKeys.forEach(keys::remove);
        List<EnterprisePermission> enterprisePermissions = new ArrayList<>(100);
        for (Map.Entry<String, List<Method>> entry : methodMap.entrySet()) {
            String key = entry.getKey();
            if (!dbKeys.contains(key)) {
                EnterprisePreAuth annotation = entry.getValue().get(0).getAnnotation(EnterprisePreAuth.class);
                enterprisePermissions.add(EnterprisePermission.builder()
                        .permissionKey(key)
                        .module(annotation.module())
                        .description(annotation.description())
                        .officer(true)
                        .governor(true)
                        .staff(true)
                        .availableExpired(false)
                        .build());
            }
        }
        enterprisePermissionService.insertBatch(enterprisePermissions);
    }

    private void throwEnterprisePermissionError(Set<String> keys, List<String> dbKeys) {
        StringBuilder errMsg = new StringBuilder();
        for (String key : keys) {
            if (!dbKeys.contains(key)) {
                errMsg.append(key).append("\n");
            }
        }
        throw new IllegalStateException("Enterprise permission misconfiguration. Table agent_enterprise_permission is missing keys:\n" + errMsg);
    }

    public List<Method> getMethodsWithAnnotation(Class<? extends Annotation> annotationType) {
        List<Method> result = new ArrayList<>(100);
        // Get all bean names
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            // Get target class (handle AOP proxies)
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            List<Method> annotatedMethods = new ArrayList<>();
            // Iterate over all methods in the class
            ReflectionUtils.doWithMethods(targetClass, method -> {
                // Find annotation on method (including meta-annotations)
                Annotation annotation = AnnotationUtils.findAnnotation(method, annotationType);
                if (annotation != null) {
                    annotatedMethods.add(method);
                }
            });
            if (!annotatedMethods.isEmpty()) {
                result.addAll(annotatedMethods);
            }
        }
        return result;
    }
}
