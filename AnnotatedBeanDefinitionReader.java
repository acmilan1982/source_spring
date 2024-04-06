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

package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
	Convenient adapter for programmatic registration of bean classes.
	This is an alternative to ClassPathBeanDefinitionScanner, applying the same resolution of annotations but for explicitly registered classes only.
	Since:
	3.0
	See Also:
	AnnotationConfigApplicationContext.register
	Author:
	Juergen Hoeller, Chris Beams, Sam Brannen, Phillip Webb
 */
public class AnnotatedBeanDefinitionReader {

	private final BeanDefinitionRegistry registry;

	private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	private ConditionEvaluator conditionEvaluator;


 
	/**
        Create a new AnnotatedBeanDefinitionReader for the given registry.
        If the registry is EnvironmentCapable, e.g. is an ApplicationContext, the Environment will be inherited, otherwise a new StandardEnvironment will be created and used.
        Params:
          registry – the BeanFactory to load bean definitions into, in the form of a BeanDefinitionRegistry
        See Also:
          AnnotatedBeanDefinitionReader(BeanDefinitionRegistry, Environment), setEnvironment(Environment)

		为参数指定的BeanDefinitionRegistry创建AnnotatedBeanDefinitionReader，
		所有 bean definitions ，都加载到该 BeanDefinitionRegistry
	 */
	// 构造方法1
	public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
		this(registry, getOrCreateEnvironment(registry));   // 见构造方法2
	}


	/**
        Create a new AnnotatedBeanDefinitionReader for the given registry, using the given Environment.
        Params:
          registry – the BeanFactory to load bean definitions into, in the form of a BeanDefinitionRegistry 
		  environment – the Environment to use when evaluating bean definition profiles.
        Since:
        3.1
	 */
	// 构造方法2
	public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, 
	                                     Environment environment) {                 // new StandardEnvironment()
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		Assert.notNull(environment, "Environment must not be null");
		this.registry = registry;
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);

		/**
			硬编码注册一系列PostProcessor，仅注册BeanDefinition
			注册  org.springframework.context.annotation.internalConfigurationAnnotationProcessor -> ConfigurationClassPostProcessor
			注册  org.springframework.context.annotation.internalAutowiredAnnotationProcessor -> AutowiredAnnotationBeanPostProcessor
			注册  org.springframework.context.annotation.internalCommonAnnotationProcessor -> CommonAnnotationBeanPostProcessor
			注册  org.springframework.context.event.internalEventListenerProcessor -> EventListenerMethodProcessor
			注册  org.springframework.context.event.internalEventListenerFactory -> DefaultEventListenerFactory
		 */
		AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);     // 见AnnotationConfigUtils代码1
	}


	/**
		 Get the Environment from the given registry if possible, otherwise return a new StandardEnvironment.
	 */
    // 代码1
	private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		if (registry instanceof EnvironmentCapable) {
			return ((EnvironmentCapable) registry).getEnvironment();   // 见AnnotationConfigApplicationContext父类AbstractApplicationContext代码1
			                                                           // 创建Environment对象，实现类为：StandardEnvironment
		}
		return new StandardEnvironment();
	}






	/**
		Register one or more component classes to be processed.
		Calls to register are idempotent; adding the same component class more than once has no additional effect.
		Params:
		  componentClasses – one or more component classes, e.g. @Configuration classes

		注册配置类，读取其上的注解
	 */
    // 代码10
	public void register(Class<?>... componentClasses) {
		for (Class<?> componentClass : componentClasses) {
			registerBean(componentClass);                            // 见代码11
		}
	}

	/**
		Register a bean from the given bean class, deriving its metadata from class-declared annotations.
		Params:
		  beanClass – the class of the bean
		
		注册一个bean，并从其 类级别的注解 派生元数据  
	 */
    // 代码11
	public void registerBean(Class<?> beanClass) {
		doRegisterBean(beanClass, null, null, null, null);           // 见代码20
	}

	 
	/**
		Register a bean from the given bean class, deriving its metadata from class-declared annotations.
		Params:
		  beanClass – the class of the bean 
		  name – an explicit name for the bean 
		  qualifiers – specific qualifier annotations to consider, if any, in addition to qualifiers at the bean class level 
		  supplier – a callback for creating an instance of the bean (may be null) 
		  customizers – one or more callbacks for customizing the factory's BeanDefinition, e.g. setting a lazy-init or primary flag
		Since:
		5.0
	 */
    // 代码20
	private <T> void doRegisterBean(Class<T> beanClass, 
									@Nullable String name,                                // null
									@Nullable Class<? extends Annotation>[] qualifiers,   // null
									@Nullable Supplier<T> supplier,                       // null
									@Nullable BeanDefinitionCustomizer[] customizers) {   // null

        // 为配置类创建对应的 BeanDefinition 对象，实现类为AnnotatedGenericBeanDefinition
		AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(beanClass);        // 见AnnotatedGenericBeanDefinition构造方法1
		
		if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
			return;
		}

		abd.setInstanceSupplier(supplier);
		ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);
		abd.setScope(scopeMetadata.getScopeName());
		String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));

		AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);
		if (qualifiers != null) {
			for (Class<? extends Annotation> qualifier : qualifiers) {
				if (Primary.class == qualifier) {
					abd.setPrimary(true);
				}
				else if (Lazy.class == qualifier) {
					abd.setLazyInit(true);
				}
				else {
					abd.addQualifier(new AutowireCandidateQualifier(qualifier));
				}
			}
		}
		if (customizers != null) {
			for (BeanDefinitionCustomizer customizer : customizers) {
				customizer.customize(abd);
			}
		}

		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);
		definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
		// 向 DefaultListableBeanFactory 中注册当前 config bean
		BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);    //
	}




}
