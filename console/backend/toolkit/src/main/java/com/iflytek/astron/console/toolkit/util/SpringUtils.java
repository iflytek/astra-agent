package com.iflytek.astron.console.toolkit.util;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Utility class for accessing Spring-managed beans and application context.
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Retrieve beans by name or type</li>
 * <li>Check bean existence and scope</li>
 * <li>Access bean aliases and type</li>
 * <li>Get AOP proxy objects</li>
 * <li>Obtain active Spring profiles</li>
 * </ul>
 *
 * <p>
 * This class stores references to the {@link ConfigurableListableBeanFactory} and
 * {@link ApplicationContext} for static access.
 * </p>
 */
@Component
public final class SpringUtils implements BeanFactoryPostProcessor, ApplicationContextAware {

    /** Spring application context bean factory */
    private static ConfigurableListableBeanFactory beanFactory;

    /** Spring application context */
    private static ApplicationContext applicationContext;

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        SpringUtils.beanFactory = beanFactory;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringUtils.applicationContext = applicationContext;
    }

    /**
     * Retrieve a bean by its name.
     *
     * @param name the name of the bean
     * @param <T> the generic type of the bean
     * @return the bean instance registered with the given name
     * @throws BeansException if no bean with the given name exists
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) throws BeansException {
        return (T) beanFactory.getBean(name);
    }

    /**
     * Retrieve a bean by its type.
     *
     * @param clz the class type of the bean
     * @param <T> the generic type of the bean
     * @return the bean instance of the given type
     * @throws BeansException if the bean cannot be created
     */
    public static <T> T getBean(Class<T> clz) throws BeansException {
        return beanFactory.getBean(clz);
    }

    /**
     * Check if the BeanFactory contains a bean definition with the given name.
     *
     * @param name the name of the bean
     * @return {@code true} if the bean definition exists, otherwise {@code false}
     */
    public static boolean containsBean(String name) {
        return beanFactory.containsBean(name);
    }

    /**
     * Determine whether the bean with the given name is a singleton or a prototype.
     *
     * @param name the name of the bean
     * @return {@code true} if the bean is a singleton, {@code false} if it is a prototype
     * @throws NoSuchBeanDefinitionException if no bean with the given name is found
     */
    public static boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
        return beanFactory.isSingleton(name);
    }

    /**
     * Get the type of the bean registered with the given name.
     *
     * @param name the name of the bean
     * @return the type of the registered object
     * @throws NoSuchBeanDefinitionException if no bean with the given name is found
     */
    public static Class<?> getType(String name) throws NoSuchBeanDefinitionException {
        return beanFactory.getType(name);
    }

    /**
     * Get all aliases associated with the given bean name.
     *
     * @param name the name of the bean
     * @return an array of alias names, or an empty array if none are found
     * @throws NoSuchBeanDefinitionException if no bean with the given name is found
     */
    public static String[] getAliases(String name) throws NoSuchBeanDefinitionException {
        return beanFactory.getAliases(name);
    }

    /**
     * Get the current AOP proxy object for the given invoker.
     *
     * @param invoker the original bean instance
     * @param <T> the generic type of the bean
     * @return the AOP proxy object of the given bean
     * @throws IllegalStateException if called outside of an AOP context
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAopProxy(T invoker) {
        return (T) AopContext.currentProxy();
    }

    /**
     * Get the currently active environment profiles.
     *
     * @return an array of active profile names, never {@code null}
     */
    public static String[] getActiveProfiles() {
        return applicationContext.getEnvironment().getActiveProfiles();
    }

    /**
     * Get the first active environment profile.
     *
     * @return the first active profile, or {@code null} if none are active
     */
    public static String getActiveProfile() {
        final String[] activeProfiles = getActiveProfiles();
        return !ObjectIsNull.check(activeProfiles) ? activeProfiles[0] : null;
    }
}
