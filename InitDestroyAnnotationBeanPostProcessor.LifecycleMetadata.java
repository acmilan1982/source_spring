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
  
 
     /**
      * Class representing information about annotated init and destroy methods.
      */
     private class LifecycleMetadata {
 
         private final Class<?> targetClass;
 
         private final Collection<LifecycleElement> initMethods;
 
         private final Collection<LifecycleElement> destroyMethods;
 
         @Nullable
         private volatile Set<LifecycleElement> checkedInitMethods;
 
         @Nullable
         private volatile Set<LifecycleElement> checkedDestroyMethods;
 
         public LifecycleMetadata(Class<?> targetClass, Collection<LifecycleElement> initMethods,
                 Collection<LifecycleElement> destroyMethods) {
 
             this.targetClass = targetClass;
             this.initMethods = initMethods;
             this.destroyMethods = destroyMethods;
         }
 
         public void checkConfigMembers(RootBeanDefinition beanDefinition) {
             Set<LifecycleElement> checkedInitMethods = new LinkedHashSet<>(this.initMethods.size());
             for (LifecycleElement element : this.initMethods) {
                 String methodIdentifier = element.getIdentifier();
                 if (!beanDefinition.isExternallyManagedInitMethod(methodIdentifier)) {
                     beanDefinition.registerExternallyManagedInitMethod(methodIdentifier);
                     checkedInitMethods.add(element);
                     if (logger.isTraceEnabled()) {
                         logger.trace("Registered init method on class [" + this.targetClass.getName() + "]: " + methodIdentifier);
                     }
                 }
             }
             Set<LifecycleElement> checkedDestroyMethods = new LinkedHashSet<>(this.destroyMethods.size());
             for (LifecycleElement element : this.destroyMethods) {
                 String methodIdentifier = element.getIdentifier();
                 if (!beanDefinition.isExternallyManagedDestroyMethod(methodIdentifier)) {
                     beanDefinition.registerExternallyManagedDestroyMethod(methodIdentifier);
                     checkedDestroyMethods.add(element);
                     if (logger.isTraceEnabled()) {
                         logger.trace("Registered destroy method on class [" + this.targetClass.getName() + "]: " + methodIdentifier);
                     }
                 }
             }
             this.checkedInitMethods = checkedInitMethods;
             this.checkedDestroyMethods = checkedDestroyMethods;
         }
 
         public void invokeInitMethods(Object target, String beanName) throws Throwable {
             Collection<LifecycleElement> checkedInitMethods = this.checkedInitMethods;
             Collection<LifecycleElement> initMethodsToIterate =
                     (checkedInitMethods != null ? checkedInitMethods : this.initMethods);
             if (!initMethodsToIterate.isEmpty()) {
                 for (LifecycleElement element : initMethodsToIterate) {
                     if (logger.isTraceEnabled()) {
                         logger.trace("Invoking init method on bean '" + beanName + "': " + element.getMethod());
                     }
                     element.invoke(target);
                 }
             }
         }
 
         public void invokeDestroyMethods(Object target, String beanName) throws Throwable {
             Collection<LifecycleElement> checkedDestroyMethods = this.checkedDestroyMethods;
             Collection<LifecycleElement> destroyMethodsToUse =
                     (checkedDestroyMethods != null ? checkedDestroyMethods : this.destroyMethods);
             if (!destroyMethodsToUse.isEmpty()) {
                 for (LifecycleElement element : destroyMethodsToUse) {
                     if (logger.isTraceEnabled()) {
                         logger.trace("Invoking destroy method on bean '" + beanName + "': " + element.getMethod());
                     }
                     element.invoke(target);
                 }
             }
         }
 
         public boolean hasDestroyMethods() {
             Collection<LifecycleElement> checkedDestroyMethods = this.checkedDestroyMethods;
             Collection<LifecycleElement> destroyMethodsToUse =
                     (checkedDestroyMethods != null ? checkedDestroyMethods : this.destroyMethods);
             return !destroyMethodsToUse.isEmpty();
         }
     }
 
  
 
 }
 