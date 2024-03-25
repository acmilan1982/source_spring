/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 package org.springframework.beans.factory.annotation;

 import java.io.IOException;
 import java.io.ObjectInputStream;
 import java.io.Serializable;
 import java.lang.annotation.Annotation;
 import java.lang.reflect.InvocationTargetException;
 import java.lang.reflect.Method;
 import java.lang.reflect.Modifier;
 import java.util.ArrayList;
 import java.util.Arrays;
 import java.util.Collection;
 import java.util.Collections;
 import java.util.LinkedHashSet;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
 import java.util.concurrent.ConcurrentHashMap;
 
 import org.apache.commons.logging.Log;
 import org.apache.commons.logging.LogFactory;
 
 import org.springframework.beans.BeansException;
 import org.springframework.beans.factory.BeanCreationException;
 import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
 import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
 import org.springframework.beans.factory.support.RootBeanDefinition;
 import org.springframework.core.Ordered;
 import org.springframework.core.PriorityOrdered;
 import org.springframework.core.annotation.AnnotationUtils;
 import org.springframework.lang.Nullable;
 import org.springframework.util.ClassUtils;
 import org.springframework.util.ReflectionUtils;
 
 /**
  * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
  * that invokes annotated init and destroy methods. Allows for an annotation
  * alternative to Spring's {@link org.springframework.beans.factory.InitializingBean}
  * and {@link org.springframework.beans.factory.DisposableBean} callback interfaces.
  *
  * <p>The actual annotation types that this post-processor checks for can be
  * configured through the {@link #setInitAnnotationType "initAnnotationType"}
  * and {@link #setDestroyAnnotationType "destroyAnnotationType"} properties.
  * Any custom annotation can be used, since there are no required annotation
  * attributes.
  *
  * <p>Init and destroy annotations may be applied to methods of any visibility:
  * public, package-protected, protected, or private. Multiple such methods
  * may be annotated, but it is recommended to only annotate one single
  * init method and destroy method, respectively.
  *
  * <p>Spring's {@link org.springframework.context.annotation.CommonAnnotationBeanPostProcessor}
  * supports the JSR-250 {@link javax.annotation.PostConstruct} and {@link javax.annotation.PreDestroy}
  * annotations out of the box, as init annotation and destroy annotation, respectively.
  * Furthermore, it also supports the {@link javax.annotation.Resource} annotation
  * for annotation-driven injection of named beans.
  *
  * @author Juergen Hoeller
  * @since 2.5
  * @see #setInitAnnotationType
  * @see #setDestroyAnnotationType
  */
 @SuppressWarnings("serial")
 public class InitDestroyAnnotationBeanPostProcessor
         implements DestructionAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor, PriorityOrdered, Serializable {
 
     private final transient LifecycleMetadata emptyLifecycleMetadata =
             new LifecycleMetadata(Object.class, Collections.emptyList(), Collections.emptyList()) {
                 @Override
                 public void checkConfigMembers(RootBeanDefinition beanDefinition) {
                 }
                 @Override
                 public void invokeInitMethods(Object target, String beanName) {
                 }
                 @Override
                 public void invokeDestroyMethods(Object target, String beanName) {
                 }
                 @Override
                 public boolean hasDestroyMethods() {
                     return false;
                 }
             };
 
 
     protected transient Log logger = LogFactory.getLog(getClass());
 
     // @PostConstruct，构造方法方法中被初始化
     @Nullable
     private Class<? extends Annotation> initAnnotationType;
 
     // @PreDestroy，构造方法方法中被初始化
     @Nullable
     private Class<? extends Annotation> destroyAnnotationType;
 
     private int order = Ordered.LOWEST_PRECEDENCE;
 
     @Nullable
     private final transient Map<Class<?>, LifecycleMetadata> lifecycleMetadataCache = new ConcurrentHashMap<>(256);
 
 
     /**
      * Specify the init annotation to check for, indicating initialization
      * methods to call after configuration of a bean.
      * <p>Any custom annotation can be used, since there are no required
      * annotation attributes. There is no default, although a typical choice
      * is the JSR-250 {@link javax.annotation.PostConstruct} annotation.
      */
     public void setInitAnnotationType(Class<? extends Annotation> initAnnotationType) {
         this.initAnnotationType = initAnnotationType;
     }
 
     /**
      * Specify the destroy annotation to check for, indicating destruction
      * methods to call when the context is shutting down.
      * <p>Any custom annotation can be used, since there are no required
      * annotation attributes. There is no default, although a typical choice
      * is the JSR-250 {@link javax.annotation.PreDestroy} annotation.
      */
     public void setDestroyAnnotationType(Class<? extends Annotation> destroyAnnotationType) {
         this.destroyAnnotationType = destroyAnnotationType;
     }
 
     public void setOrder(int order) {
         this.order = order;
     }
 
     @Override
     public int getOrder() {
         return this.order;
     }
 
 
     // 代码5
     @Override
     public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
         LifecycleMetadata metadata = findLifecycleMetadata(beanType);        
         metadata.checkConfigMembers(beanDefinition);
     }
 


     // 代码6
     private LifecycleMetadata findLifecycleMetadata(Class<?> clazz) {
        if (this.lifecycleMetadataCache == null) {
            // Happens after deserialization, during destruction...
            return buildLifecycleMetadata(clazz);
        }
        
        // Quick check on the concurrent map first, with minimal locking.
        LifecycleMetadata metadata = this.lifecycleMetadataCache.get(clazz);
        if (metadata == null) {
            synchronized (this.lifecycleMetadataCache) {
                metadata = this.lifecycleMetadataCache.get(clazz);
                if (metadata == null) {
                    metadata = buildLifecycleMetadata(clazz);
                    this.lifecycleMetadataCache.put(clazz, metadata);
                }
                return metadata;
            }
        }
        return metadata;
    }
    

    // 遍历整个类的继承体系，
    // 代码7
    private LifecycleMetadata buildLifecycleMetadata(final Class<?> clazz) {
        // 判断Class是否持有这些注解： @PostConstruct，@PreDestroy
        if (!AnnotationUtils.isCandidateClass(clazz, Arrays.asList(this.initAnnotationType, this.destroyAnnotationType))) {
            return this.emptyLifecycleMetadata;
        }

        List<LifecycleElement> initMethods = new ArrayList<>();
        List<LifecycleElement> destroyMethods = new ArrayList<>();
        Class<?> targetClass = clazz;

        do {
            final List<LifecycleElement> currInitMethods = new ArrayList<>();
            final List<LifecycleElement> currDestroyMethods = new ArrayList<>();

            // @PostConstruct，@PreDestroy 只能注解在方法上，因此，遍历方法即可
            // 
            ReflectionUtils.doWithLocalMethods(targetClass, method -> {
                if (this.initAnnotationType != null && method.isAnnotationPresent(this.initAnnotationType)) {
                    LifecycleElement element = new LifecycleElement(method);
                    currInitMethods.add(element);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Found init method on class [" + clazz.getName() + "]: " + method);
                    }
                }
                if (this.destroyAnnotationType != null && method.isAnnotationPresent(this.destroyAnnotationType)) {
                    currDestroyMethods.add(new LifecycleElement(method));
                    if (logger.isTraceEnabled()) {
                        logger.trace("Found destroy method on class [" + clazz.getName() + "]: " + method);
                    }
                }
            });

            initMethods.addAll(0, currInitMethods);
            destroyMethods.addAll(currDestroyMethods);
            targetClass = targetClass.getSuperclass();
        }
        while (targetClass != null && targetClass != Object.class);

        return (initMethods.isEmpty() && destroyMethods.isEmpty() ? this.emptyLifecycleMetadata :
                new LifecycleMetadata(clazz, initMethods, destroyMethods));
    }












    
     @Override
     public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
         LifecycleMetadata metadata = findLifecycleMetadata(bean.getClass());
         try {
             metadata.invokeInitMethods(bean, beanName);
         }
         catch (InvocationTargetException ex) {
             throw new BeanCreationException(beanName, "Invocation of init method failed", ex.getTargetException());
         }
         catch (Throwable ex) {
             throw new BeanCreationException(beanName, "Failed to invoke init method", ex);
         }
         return bean;
     }
 
     @Override
     public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
         return bean;
     }
 
     @Override
     public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
         LifecycleMetadata metadata = findLifecycleMetadata(bean.getClass());
         try {
             metadata.invokeDestroyMethods(bean, beanName);
         }
         catch (InvocationTargetException ex) {
             String msg = "Destroy method on bean with name '" + beanName + "' threw an exception";
             if (logger.isDebugEnabled()) {
                 logger.warn(msg, ex.getTargetException());
             }
             else {
                 logger.warn(msg + ": " + ex.getTargetException());
             }
         }
         catch (Throwable ex) {
             logger.warn("Failed to invoke destroy method on bean with name '" + beanName + "'", ex);
         }
     }
 
     @Override
     public boolean requiresDestruction(Object bean) {
         return findLifecycleMetadata(bean.getClass()).hasDestroyMethods();
     }
 

 

 
     //---------------------------------------------------------------------
     // Serialization support
     //---------------------------------------------------------------------
 
     private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
         // Rely on default serialization; just initialize state after deserialization.
         ois.defaultReadObject();
 
         // Initialize transient fields.
         this.logger = LogFactory.getLog(getClass());
     }
 
 
  
 
 }
 