package io.github.genichimaruo.singulo.test.harness;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
public @interface GameTest {
 String template() default "";
 int timeoutTicks() default 100;
 String batch() default "defaultBatch";
}
