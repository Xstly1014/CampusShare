package com.campushare.agent.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    String name();

    String description();

    boolean required() default false;

    String type() default "string";

    String[] enumValues() default {};
}
