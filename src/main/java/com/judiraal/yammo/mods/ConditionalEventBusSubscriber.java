package com.judiraal.yammo.mods;

import net.neoforged.api.distmarker.Dist;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConditionalEventBusSubscriber {
    Dist[] value() default { Dist.CLIENT, Dist.DEDICATED_SERVER };

    String[] dependencies() default {};
}
