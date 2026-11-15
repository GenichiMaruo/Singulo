package io.github.genichimaruo.singulo.test.harness;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AfterBatch { String batch(); }
