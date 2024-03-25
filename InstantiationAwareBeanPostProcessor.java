/*
 * Copyright 2002-2021 the original author or authors.
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

 import java.beans.PropertyDescriptor;
 
 import org.springframework.beans.BeansException;
 import org.springframework.beans.PropertyValues;
 import org.springframework.lang.Nullable;
 
 /**
  * Subinterface of {@link BeanPostProcessor} that adds a before-instantiation callback,
  * and a callback after instantiation but before explicit properties are set or
  * autowiring occurs.
  *
  * <p>Typically used to suppress default instantiation for specific target beans,
  * for example to create proxies with special TargetSources (pooling targets,
  * lazily initializing targets, etc), or to implement additional injection strategies
  * such as field injection.
  *
  * <p><b>NOTE:</b> This interface is a special purpose interface, mainly for
  * internal use within the framework. It is recommended to implement the plain
  * {@link BeanPostProcessor} interface as far as possible.
  *
  * @author Juergen Hoeller
  * @author Rod Johnson
  * @since 1.2
  * @see org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#setCustomTargetSourceCreators
  * @see org.springframework.aop.framework.autoproxy.target.LazyInitTargetSourceCreator
  */
 public interface InstantiationAwareBeanPostProcessor extends BeanPostProcessor {
 
     /**
        Apply this BeanPostProcessor before the target bean gets instantiated. 
        The returned bean object may be a proxy to use instead of the target bean, effectively suppressing default instantiation of the target bean.
        在实例化目标bean之前应用此BeanPostProcessor。返回的bean对象可能是要使用的代理，而不是目标bean，从而有效地抑制了目标bean的默认实例化。


        If a non-null object is returned by this method, the bean creation process will be short-circuited. 
        The only further processing applied is the postProcessAfterInitialization callback from the configured BeanPostProcessors.
        如果此方法返回非null对象，则bean创建过程将短路(不会执行后面通用的实例化，依赖注入等流程，该非null对象就被视为创建完成的bean)。
        唯一的进一步处理是执行BeanPostProcessors的postProcessAfterInitialization。

        This callback will be applied to bean definitions with their bean class, 
        as well as to factory-method definitions in which case the returned bean type will be passed in here.
        这个回调将应用于带有bean类的bean定义，以及工厂方法定义，在这种情况下，返回的bean类型将在这里传递。

        Post-processors may implement the extended SmartInstantiationAwareBeanPostProcessor interface in order to predict the type of the bean object that they are going to return here.
        后处理器可以实现扩展的SmartInstantiationAwareBeanPostProcessor接口，以便预测它们将在此处返回的bean对象的类型。
        
        The default implementation returns null.
        默认实现返回null。
        
        Params:
            beanClass – the class of the bean to be instantiated 
            beanName – the name of the bean
        Returns:
            the bean object to expose instead of a default instance of the target bean, or null to proceed with default instantiation
        Throws:
            BeansException – in case of errors
        See Also:
            postProcessAfterInstantiation, 
            org.springframework.beans.factory.support.AbstractBeanDefinition.getBeanClass(), 
            org.springframework.beans.factory.support.AbstractBeanDefinition.getFactoryMethodName()


      */
     @Nullable
     default Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
         return null;
     }
 
     /**
        Perform operations after the bean has been instantiated, via a constructor or factory method, but before Spring property population (from explicit properties or autowiring) occurs.
        当前方法的调用时机：通过构造函数或工厂方法实例化bean之后，Spring属性填充（显式或自动装配）发生之前

        This is the ideal callback for performing custom field injection on the given bean instance, right before Spring's autowiring kicks in.
        这是在Spring的自动装配之前，对给定的bean实例执行自定义字段注入的理想回调。

        The default implementation returns true.

        Params:
            bean – the bean instance created, with properties not having been set yet 
            beanName – the name of the bean
        Returns:
            true if properties should be set on the bean;      true， 继续属性填充(依赖注入)
            false if property population should be skipped.    false: 属性填充(依赖注入)会被跳过
            Normal implementations should return true. 
            Returning false will also prevent any subsequent InstantiationAwareBeanPostProcessor instances being invoked on this bean instance.
        Throws:
            BeansException – in case of errors
        See Also:
            postProcessBeforeInstantiation



      */
     default boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
         return true;
     }
 
     /**
        Post-process the given property values before the factory applies them to the given bean, without any need for property descriptors.
        
        Implementations should return null (the default) if they provide a custom postProcessPropertyValues implementation, and pvs otherwise. 
        In a future version of this interface (with postProcessPropertyValues removed), the default implementation will return the given pvs as-is directly.
        Params:
        pvs – the property values that the factory is about to apply (never null) bean – the bean instance created, but whose properties have not yet been set beanName – the name of the bean
        Returns:
        the actual property values to apply to the given bean (can be the passed-in PropertyValues instance), or null which proceeds with the existing properties but specifically continues with a call to postProcessPropertyValues (requiring initialized PropertyDescriptors for the current bean class)
        Throws:
        BeansException – in case of errors
        Since:
        5.1
        See Also:
        postProcessPropertyValues 
      */
     @Nullable
     default PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName)
             throws BeansException {
 
         return null;
     }
 
     /**
      * Post-process the given property values before the factory applies them
      * to the given bean. Allows for checking whether all dependencies have been
      * satisfied, for example based on a "Required" annotation on bean property setters.
      * <p>Also allows for replacing the property values to apply, typically through
      * creating a new MutablePropertyValues instance based on the original PropertyValues,
      * adding or removing specific values.
      * <p>The default implementation returns the given {@code pvs} as-is.
      * @param pvs the property values that the factory is about to apply (never {@code null})
      * @param pds the relevant property descriptors for the target bean (with ignored
      * dependency types - which the factory handles specifically - already filtered out)
      * @param bean the bean instance created, but whose properties have not yet been set
      * @param beanName the name of the bean
      * @return the actual property values to apply to the given bean (can be the passed-in
      * PropertyValues instance), or {@code null} to skip property population
      * @throws org.springframework.beans.BeansException in case of errors
      * @see #postProcessProperties
      * @see org.springframework.beans.MutablePropertyValues
      * @deprecated as of 5.1, in favor of {@link #postProcessProperties(PropertyValues, Object, String)}
      */
     @Deprecated
     @Nullable
     default PropertyValues postProcessPropertyValues(
             PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeansException {
 
         return pvs;
     }
 
 }
 