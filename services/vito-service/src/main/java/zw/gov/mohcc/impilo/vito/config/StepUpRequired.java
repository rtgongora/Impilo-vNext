package zw.gov.mohcc.impilo.vito.config;

import java.lang.annotation.*;

/**
 * Methods annotated with @StepUpRequired will reject requests that
 * lack a valid step-up token (X-Step-Up-Token header).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StepUpRequired {
    String reason() default "This action requires step-up authentication";
}
