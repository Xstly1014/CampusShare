package com.campushare.agent.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolDef {
    String name();

    String description();

    String[] intent() default {};

    boolean readOnly() default true;

    int timeoutMs() default 10000;
}
