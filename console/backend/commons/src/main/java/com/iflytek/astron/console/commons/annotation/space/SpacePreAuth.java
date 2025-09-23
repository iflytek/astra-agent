package com.iflytek.astron.console.commons.annotation.space;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SpacePreAuth {

    /**
     * Authorization key, ideally globally unique, maintained in table (agent_space_permission). Default
     * format: ClassName_MethodName_HTTPMethod, e.g., UserController_add_POST
     */
    String key();

    // Permission module (for description only)
    String module() default "";

    // Permission point (for description only)
    String point() default "";

    // Description of operation (for description only)
    String description() default "";

    // Whether spaceId is required
    boolean requireSpaceId() default false;
}
