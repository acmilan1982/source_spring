/*
 * Copyright 2002-2019 the original author or authors.
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

 package org.springframework.beans.factory.config;

 import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.lang.Nullable;
 
 /**
  * Factory hook that allows for custom modification of new bean instances &mdash;
  * for example, checking for marker interfaces or wrapping beans with proxies.
  *
  * <p>Typically, post-processors that populate beans via marker interfaces
  * or the like will implement {@link #postProcessBeforeInitialization},
  * while post-processors that wrap beans with proxies will normally
  * implement {@link #postProcessAfterInitialization}.
  *
  * <h3>Registration</h3>
  * <p>An {@code ApplicationContext} can autodetect {@code BeanPostProcessor} beans
  * in its bean definitions and apply those post-processors to any beans subsequently
  * created. A plain {@code BeanFactory} allows for programmatic registration of
  * post-processors, applying them to all beans created through the bean factory.
  *
  * <h3>Ordering</h3>
  * <p>{@code BeanPostProcessor} beans that are autodetected in an
  * {@code ApplicationContext} will be ordered according to
  * {@link org.springframework.core.PriorityOrdered} and
  * {@link org.springframework.core.Ordered} semantics. In contrast,
  * {@code BeanPostProcessor} beans that are registered programmatically with a
  * {@code BeanFactory} will be applied in the order of registration; any ordering
  * semantics expressed through implementing the
  * {@code PriorityOrdered} or {@code Ordered} interface will be ignored for
  * programmatically registered post-processors. Furthermore, the
  * {@link org.springframework.core.annotation.Order @Order} annotation is not
  * taken into account for {@code BeanPostProcessor} beans.
  *
  * @author Juergen Hoeller
  * @author Sam Brannen
  * @since 10.10.2003
  * @see InstantiationAwareBeanPostProcessor
  * @see DestructionAwareBeanPostProcessor
  * @see ConfigurableBeanFactory#addBeanPostProcessor
  * @see BeanFactoryPostProcessor
  */
 public interface BeanPostProcessor {
 
     /**
        Apply this BeanPostProcessor to the given new bean instance before any bean initialization callbacks 
        (like InitializingBean's afterPropertiesSet or a custom init-method). 
        The bean will already be populated with property values. 
        The returned bean instance may be a wrapper around the original.

        The default implementation returns the given bean as-is.
        Params:
          bean – the new bean instance beanName – the name of the bean
        Returns:
          the bean instance to use, either the original or a wrapped one; if null, no subsequent BeanPostProcessors will be invoked
        Throws:
          BeansException – in case of errors
        See Also:
          org.springframework.beans.factory.InitializingBean.afterPropertiesSet
      */
     @Nullable
     default Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
         return bean;
     }
 
     /**
        Apply this BeanPostProcessor to the given new bean instance after any bean initialization callbacks (like InitializingBean's afterPropertiesSet or a custom init-method). 
        The bean will already be populated with property values. The returned bean instance may be a wrapper around the original.
        
        In case of a FactoryBean, this callback will be invoked for both the FactoryBean instance and the objects created by the FactoryBean (as of Spring 2.0). 
        The post-processor can decide whether to apply to either the FactoryBean or created objects or both through corresponding bean instanceof FactoryBean checks.
        
        This callback will also be invoked after a short-circuiting triggered by a InstantiationAwareBeanPostProcessor.postProcessBeforeInstantiation method, in contrast to all other BeanPostProcessor callbacks.
        The default implementation returns the given bean as-is.
        Params:
        bean – the new bean instance beanName – the name of the bean
        Returns:
        the bean instance to use, either the original or a wrapped one; if null, no subsequent BeanPostProcessors will be invoked
        Throws:
        BeansException – in case of errors
        See Also:
        org.springframework.beans.factory.InitializingBean.afterPropertiesSet, org.springframework.beans.factory.FactoryBean
      */
     @Nullable
     default Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
         return bean;
     }
 
 }
 