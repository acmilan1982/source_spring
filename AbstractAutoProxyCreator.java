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

package org.springframework.aop.framework.autoproxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.aop.Advice;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyProcessorSupport;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.SmartClassLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that wraps each eligible bean with an AOP proxy, delegating to specified interceptors
 * before invoking the bean itself.
 *
 * <p>This class distinguishes between "common" interceptors: shared for all proxies it
 * creates, and "specific" interceptors: unique per bean instance. There need not be any
 * common interceptors. If there are, they are set using the interceptorNames property.
 * As with {@link org.springframework.aop.framework.ProxyFactoryBean}, interceptors names
 * in the current factory are used rather than bean references to allow correct handling
 * of prototype advisors and interceptors: for example, to support stateful mixins.
 * Any advice type is supported for {@link #setInterceptorNames "interceptorNames"} entries.
 *
 * <p>Such auto-proxying is particularly useful if there's a large number of beans that
 * need to be wrapped with similar proxies, i.e. delegating to the same interceptors.
 * Instead of x repetitive proxy definitions for x target beans, you can register
 * one single such post processor with the bean factory to achieve the same effect.
 *
 * <p>Subclasses can apply any strategy to decide if a bean is to be proxied, e.g. by type,
 * by name, by definition details, etc. They can also return additional interceptors that
 * should just be applied to the specific bean instance. A simple concrete implementation is
 * {@link BeanNameAutoProxyCreator}, identifying the beans to be proxied via given names.
 *
 * <p>Any number of {@link TargetSourceCreator} implementations can be used to create
 * a custom target source: for example, to pool prototype objects. Auto-proxying will
 * occur even if there is no advice, as long as a TargetSourceCreator specifies a custom
 * {@link org.springframework.aop.TargetSource}. If there are no TargetSourceCreators set,
 * or if none matches, a {@link org.springframework.aop.target.SingletonTargetSource}
 * will be used by default to wrap the target bean instance.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Rob Harrop
 * @since 13.10.2003
 * @see #setInterceptorNames
 * @see #getAdvicesAndAdvisorsForBean
 * @see BeanNameAutoProxyCreator
 * @see DefaultAdvisorAutoProxyCreator
 */
@SuppressWarnings("serial")
public abstract class AbstractAutoProxyCreator extends ProxyProcessorSupport
		implements SmartInstantiationAwareBeanPostProcessor, BeanFactoryAware {

	/**
	 * Convenience constant for subclasses: Return value for "do not proxy".
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	@Nullable
	protected static final Object[] DO_NOT_PROXY = null;

	/**
	 * Convenience constant for subclasses: Return value for
	 * "proxy without additional interceptors, just the common ones".
	 * @see #getAdvicesAndAdvisorsForBean
	 */
	protected static final Object[] PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS = new Object[0];


	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/** Default is global AdvisorAdapterRegistry. */
	private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();

	/**
	 * Indicates whether or not the proxy should be frozen. Overridden from super
	 * to prevent the configuration from becoming frozen too early.
	 */
	private boolean freezeProxy = false;

	/** Default is no common interceptors. */
	private String[] interceptorNames = new String[0];

	private boolean applyCommonInterceptorsFirst = true;

	@Nullable
	private TargetSourceCreator[] customTargetSourceCreators;

	@Nullable
	private BeanFactory beanFactory;

	private final Set<String> targetSourcedBeans = Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	private final Map<Object, Object> earlyProxyReferences = new ConcurrentHashMap<>(16);

	private final Map<Object, Class<?>> proxyTypes = new ConcurrentHashMap<>(16);

	private final Map<Object, Boolean> advisedBeans = new ConcurrentHashMap<>(256);


	 
    /**
        Create a proxy with the configured interceptors if the bean is identified as one to proxy by the subclass.
        See Also:
        getAdvicesAndAdvisorsForBean    

        执行BeanPostProcessor的postProcessAfterInitialization方法
		自动代理机制会在这一步创建代理对象

		当程序运行到这里时，目标对象已经创建完成，并且已经执行了目标对象的init_method方法等(完整初始化)
		postProcessAfterInitialization方法实际上会创建目标对象的代理对象							  
		postProcessAfterInitialization实现定义在他们的公共父类AbstractAutoProxyCreator中	
    */
    // 代码5
	@Override
	public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
		if (bean != null) {
			Object cacheKey = getCacheKey(bean.getClass(), beanName);
			if (this.earlyProxyReferences.remove(cacheKey) != bean) {
				return wrapIfNecessary(bean, beanName, cacheKey);  // 见代码10
			}
		}
		return bean;
	}


	/**
	 * Build a cache key for the given bean class and bean name.
	 * <p>Note: As of 4.2.3, this implementation does not return a concatenated
	 * class/name String anymore but rather the most efficient cache key possible:
	 * a plain bean name, prepended with {@link BeanFactory#FACTORY_BEAN_PREFIX}
	 * in case of a {@code FactoryBean}; or if no bean name specified, then the
	 * given bean {@code Class} as-is.
	 * @param beanClass the bean class
	 * @param beanName the bean name
	 * @return the cache key for the given class and name
	 */
	protected Object getCacheKey(Class<?> beanClass, @Nullable String beanName) {
		if (StringUtils.hasLength(beanName)) {
			return (FactoryBean.class.isAssignableFrom(beanClass) ?
					BeanFactory.FACTORY_BEAN_PREFIX + beanName : beanName);
		}
		else {
			return beanClass;
		}
	}

    /**
        Wrap the given bean if necessary, i.e. if it is eligible for being proxied.

        Params:
          bean – the raw bean instance 
          beanName – the name of the bean 
          cacheKey – the cache key for metadata access
        Returns:
          a proxy wrapping the bean, or the raw bean instance as-is       
    */
    // 代码10
	protected Object wrapIfNecessary(Object bean, 
                                     String beanName, 
                                     Object cacheKey) {
		if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
			return bean;
		}
		if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
			return bean;
		}
		/**
		isInfrastructureClass：
				Return whether the given bean class represents an infrastructure class that should never be proxied. 
				
				Default implementation considers Advisors, Advices and AbstractAutoProxyCreators as infrastructure classes.
			
				自动代理机制会对所有bean尝试创建其代理对象，但是对于某些bean(infrastructure)，不需要创建代理对象：
					比如AOP的组件，如Advisor，Advice，AopInfrastructureBean
				
				isInfrastructureClass方法的实现默认对Advisor，Advice，AopInfrastructureBean不进行自动代理,见代码15
				子类可以重写当前实现，如AnnotationAwareAspectJAutoProxyCreator，除了当前类默认实现之外，被@Aspect注释的类也不进行自动代理,见该类代码6
			
			shouldSkip：
				Subclasses should override this method to return true 
				if the given bean should not be considered for auto-proxying by this post-processor. 
				
				Sometimes we need to be able to avoid this happening if it will lead to a circular reference. This implementation returns false.
				
				shouldSkip的作用与isInfrastructureClass相似，默认实现返回false,见代码16
				AspectJAwareAdvisorAutoProxyCreator重写了该实现，

				
		**/		
		if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
			this.advisedBeans.put(cacheKey, Boolean.FALSE);
			return bean;
		}



	/* 
			getAdvicesAndAdvisorsForBean方法：
				Return whether the given bean is to be proxied, 
				what additional advices (e.g. AOP Alliance interceptors) and advisors to apply.
		
				Returns:
					an array of additional interceptors for the particular bean; 
					or an empty array if no additional interceptors but just the common ones; 
					or null if no proxy at all, not even with the common interceptors. 
					See constants DO_NOT_PROXY and PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS.
				
			getAdvicesAndAdvisorsForBean方法从容器中获取可用于当前bean的Advisor，由于可能有多个Advisor，所以使用数组来保存这些Advisor：
			
			如果仅考虑InfrastructureAdvisorAutoProxyCreator
						DefaultAdvisorAutoProxyCreator
						AnnotationAwareAspectJAutoProxyCreator这三种自动代理机制，那么：
			该方法的返回值有2种： 1. 适用于当前目标对象的Advisor数组
									2. DO_NOT_PROXY： 没有适用于当前目标对象的Advisor，不创建代理对象
			
			step1: 获取候选Advisor
					DefaultAdvisorAutoProxyCreator的Advisor来源于配置文件(仅使用配置文件中的Advisor，忽略Advice)  
					而AnnotationAwareAspectJAutoProxyCreator的Advisor来源于配置文件(仅使用配置文件中的Advisor，忽略Advice)和Aspect
			
			step1: 从候选Advisor中选出可用于当前bean的可用Advisor
				使用Advisor中的Pointcut进行初步匹配，即类和静态的方法匹配，排除不可匹配的Advisor
				但由于尚未能获取方法的参数，所以无法进行动态的方法匹配，直到方法被调用时，才能进行动态的方法匹配
				因此，只要类和静态的方法能匹配，该Advisor即为可用Advisor
			
			getAdvicesAndAdvisorsForBean方法会调用子类的方法，来实现自定义的Advisor获取方法和过滤方法			       
		*/			
		// Create proxy if we have advice.
		Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);  // 见子类 AbstractAdvisorAutoProxyCreator 代码5

		if (specificInterceptors != DO_NOT_PROXY) {
			this.advisedBeans.put(cacheKey, Boolean.TRUE);

			Object proxy = createProxy(bean.getClass(), 
									   beanName, 
									   specificInterceptors, 
									   new SingletonTargetSource(bean));
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}

		this.advisedBeans.put(cacheKey, Boolean.FALSE);
		return bean;
	}
 

    /**
        Create an AOP proxy for the given bean.
        Params:
        beanClass – the class of the bean beanName – the name of the bean specificInterceptors – the set of interceptors that is specific to this bean (may be empty, but not null) targetSource – the TargetSource for the proxy, already pre-configured to access the bean
        Returns:
        the AOP proxy for the bean
        See Also:
        buildAdvisors

		根据参数指定的bean实例，应用于其上的Advisor，创建其代理对象
    */
    // 代码20
	protected Object createProxy(Class<?> beanClass, 
								 @Nullable String beanName,
								 @Nullable Object[] specificInterceptors, 
								 TargetSource targetSource) {

		if (this.beanFactory instanceof ConfigurableListableBeanFactory) {
			AutoProxyUtils.exposeTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName, beanClass);
		}

		// ProxyFactory继承了AdvisedSupport
		
		ProxyFactory proxyFactory = new ProxyFactory();
		/*
			ProxyConfig包含了创建代理对象时所需的控制信息
			当前对象(AbstractAutoProxyCreator)继承了ProxyConfig,因此配置自动代理机制时，可有选择的修改ProxyConfig中的控制信息
							
			ProxyFactory也继承了ProxyConfig
			因此ProxyFactory从当前对象中复制其ProxyConfig，默认属性值都是false
			见ProxyConfig代码1
		*/	
		proxyFactory.copyFrom(this);

		if (proxyFactory.isProxyTargetClass()) {
			// Explicit handling of JDK proxy targets (for introduction advice scenarios)
			if (Proxy.isProxyClass(beanClass)) {
				// Must allow for introductions; can't just set interfaces to the proxy's interfaces only.
				for (Class<?> ifc : beanClass.getInterfaces()) {
					proxyFactory.addInterface(ifc);
				}
			}
		}
		else {
			/*					  
				总的来说，所有都是默认设置的情况下(默认规则)：
				如果目标对象实现了接口，就使用jdk动态代理创建代理对象
				如果目标对象没有实现接口，就使用cglib创建代理对象
				
				另外，有两种方式可以改变以上规则(附加规则)：在目标对象实现了接口的前提下，可以通过某些设置强制使用cglib实现代理类，比如：

				1. ProxyConfig的proxyTargetClass设置为true(默认值为false)，则无论如何创建基于类的代理，即使bean实现了接口
					注意，所有bean都会创建基于类的代理
				2. BeanDefinition的preserveTargetClass设置为true(默认值为false)，则对于该bean，创建基于类的代理，即使bean实现了接口		
					注意，仅当前bean都会创建基于类的代理，其余bean按照默认规则
				
				shouldProxyTargetClass方法判断是否定义了附加规则，如未定义，则使用默认规则 
				把目标对象所有的接口，都设置至AdvisedSupport
			*/	
			// No proxyTargetClass flag enforced, let's apply our default checks...
			if (shouldProxyTargetClass(beanClass, beanName)) {
				proxyFactory.setProxyTargetClass(true);
			}
			else {
				evaluateProxyInterfaces(beanClass, proxyFactory);
			}
		}

		Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);

		//所有的Advisor对象，设置至AdvisedSupport
		proxyFactory.addAdvisors(advisors);
		//设置目标对象，见AdvisedSupport类代码3
		proxyFactory.setTargetSource(targetSource);
		//当前版本，该方法的默认实现是什么都不做
		customizeProxyFactory(proxyFactory);

		proxyFactory.setFrozen(this.freezeProxy);
					/*
			       子类AbstractAdvisorAutoProxyCreator实现了advisorsPreFiltered()方法，返回值为true:
			       This auto-proxy creator always returns pre-filtered Advisors.
			       
			       
			       advisorsPreFiltered()表示当前的自动代理机制是否进行了Advisor的初步匹配，即类和静态的方法匹配
			       如果进行初步匹配，那么稍后会省略静态匹配，而直接进行动态匹配(如有必要)
			       
			       由上述可知，DefaultAdvisorAutoProxyCreator和AnnotationAwareAspectJAutoProxyCreator已经进行了初步匹配
			       因此重写了该方法，总是返回true
			       见子类AbstractAdvisorAutoProxyCreator代码6
			       
					*/	
		if (advisorsPreFiltered()) {
			proxyFactory.setPreFiltered(true);
		}

		// Use original ClassLoader if bean class not locally loaded in overriding class loader
		ClassLoader classLoader = getProxyClassLoader();
		if (classLoader instanceof SmartClassLoader && classLoader != beanClass.getClassLoader()) {
			classLoader = ((SmartClassLoader) classLoader).getOriginalClassLoader();
		}

        // ProxyFactory proxyFactory = new ProxyFactory();
		return proxyFactory.getProxy(classLoader);  // 见ProxyFactory代码5
	}

	 

}
