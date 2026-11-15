package io.github.genichimaruo.singulo.test.harness;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import java.lang.reflect.*;
import java.util.*;
/** Restores legacy per-batch cleanup through the native environment lifecycle, including failed tests. */
public record LegacyBatchEnvironment(String environment) implements TestEnvironmentDefinition<Boolean> {
 public static final MapCodec<LegacyBatchEnvironment> CODEC=Codec.STRING.fieldOf("environment").xmap(LegacyBatchEnvironment::new,LegacyBatchEnvironment::environment);
 @Override public Boolean setup(ServerLevel level){ return true; }
 @Override public void teardown(ServerLevel level,Boolean state){
  Set<Method> callbacks=new LinkedHashSet<>();
  for(var test:PortGameTests.definitions()) {
   if(!test.environment().toString().equals(environment))continue;
   for(Method method:test.method().getDeclaringClass().getDeclaredMethods()){
    var annotation=method.getAnnotation(AfterBatch.class);
    if(annotation!=null && annotation.batch().equals(test.batch()))callbacks.add(method);
   }
  }
  RuntimeException failure=null;
  for(Method method:callbacks){
   try {
    if(!Modifier.isStatic(method.getModifiers()) || method.getParameterCount()!=1 || method.getParameterTypes()[0]!=ServerLevel.class)
      throw new IllegalStateException("Invalid AfterBatch signature: "+method);
    method.invoke(null,level);
   }catch(ReflectiveOperationException|RuntimeException e){
    var cause=e instanceof InvocationTargetException invocation?invocation.getCause():e;
    if(failure==null)failure=new RuntimeException("AfterBatch cleanup failed for "+environment,cause);else failure.addSuppressed(cause);
   }
  }
  if(failure!=null)throw failure;
 }
 @Override public MapCodec<LegacyBatchEnvironment> codec(){return CODEC;}
}
