/*
 * Copyright 2002-2020 the original author or authors.
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

import java.util.Arrays;
import java.util.function.Supplier;

import javax.security.auth.login.Configuration;

import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Standalone application context, accepting <em>component classes</em> as input &mdash;
 * in particular {@link Configuration @Configuration}-annotated classes, but also plain
 * {@link org.springframework.stereotype.Component @Component} types and JSR-330 compliant
 * classes using {@code javax.inject} annotations.
 *
 * <p>Allows for registering classes one by one using {@link #register(Class...)}
 * as well as for classpath scanning using {@link #scan(String...)}.
 *
 * <p>In case of multiple {@code @Configuration} classes, {@link Bean @Bean} methods
 * defined in later classes will override those defined in earlier classes. This can
 * be leveraged to deliberately override certain bean definitions via an extra
 * {@code @Configuration} class.
 *
 * <p>See {@link Configuration @Configuration}'s javadoc for usage examples.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 3.0
 * @see #register
 * @see #scan
 * @see AnnotatedBeanDefinitionReader
 * @see ClassPathBeanDefinitionScanner
 * @see org.springframework.context.support.GenericXmlApplicationContext
 */
public class AnnotationConfigApplicationContext extends GenericApplicationContext implements AnnotationConfigRegistry {

	private final AnnotatedBeanDefinitionReader reader;

	private final ClassPathBeanDefinitionScanner scanner;


	/**
        Create a new AnnotationConfigApplicationContext, 
        deriving bean definitions from the given component classes and automatically refreshing the context.
        Params:
         componentClasses – one or more component classes — for example, @Configuration classes

		创建AnnotationConfigApplicationContext，从参数指定的配置类开始，衍生一系列 bean definitions
	 */
    // 构造方法1
	public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
 

		/*    
		 *    先调用父类构造方法，见GenericApplicationContext构造方法，主要进行了以下处理：
		 *    1、 创建ApplicationContext持有的 BeanFactory ，实现类为： DefaultListableBeanFactory
		 *    2、 创建ResourcePatternResolver对象
		 * 
		 * 
		 * 
		 */
		// this(): 创建 AnnotatedBeanDefinitionReader ，ClassPathBeanDefinitionScanner
		this();                       // 见构造方法2
		// 注册配置类
		register(componentClasses);   // 见代码10
		refresh();                    // 见父类 AbstractApplicationContext 代码10
	}

	/**
        Create a new AnnotationConfigApplicationContext that needs to be populated through register calls and then manually refreshed.
	 */
    // 构造方法2
	public AnnotationConfigApplicationContext() {
		StartupStep createAnnotatedBeanDefReader = this.getApplicationStartup().start("spring.context.annotated-bean-reader.create");
		/**
		 * 
			AnnotatedBeanDefinitionReader构造方法中会硬编码注册一系列PostProcessor，仅注册BeanDefinition
			注册  org.springframework.context.annotation.internalConfigurationAnnotationProcessor -> ConfigurationClassPostProcessor，其实现了BeanDefinitionRegistryPostProcessor，PriorityOrdered接口
			注册  org.springframework.context.annotation.internalAutowiredAnnotationProcessor -> AutowiredAnnotationBeanPostProcessor
			注册  org.springframework.context.annotation.internalCommonAnnotationProcessor -> CommonAnnotationBeanPostProcessor
			注册  org.springframework.context.event.internalEventListenerProcessor -> EventListenerMethodProcessor
			注册  org.springframework.context.event.internalEventListenerFactory -> DefaultEventListenerFactory
		 */
		this.reader = new AnnotatedBeanDefinitionReader(this);
		createAnnotatedBeanDefReader.end();
		this.scanner = new ClassPathBeanDefinitionScanner(this);    // 见ClassPathBeanDefinitionScanner构造方法
	}

	/**
	 * Create a new AnnotationConfigApplicationContext with the given DefaultListableBeanFactory.
	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
	 */
	public AnnotationConfigApplicationContext(DefaultListableBeanFactory beanFactory) {
		super(beanFactory);
		this.reader = new AnnotatedBeanDefinitionReader(this);
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}



	/**
	 * Create a new AnnotationConfigApplicationContext, scanning for components
	 * in the given packages, registering bean definitions for those components,
	 * and automatically refreshing the context.
	 * @param basePackages the packages to scan for component classes
	 */
	public AnnotationConfigApplicationContext(String... basePackages) {
		this();
		scan(basePackages);
		refresh();
	}




	//---------------------------------------------------------------------
	// Implementation of AnnotationConfigRegistry
	//---------------------------------------------------------------------

	/**
		Register one or more component classes to be processed.
		Note that refresh() must be called in order for the context to fully process the new classes.
		Params:
		  componentClasses – one or more component classes — for example, @Configuration classes
		See Also:
		  scan(String...), refresh()

		注册一个或多个配置类
	 */
    // 代码10
	@Override
	public void register(Class<?>... componentClasses) {
		Assert.notEmpty(componentClasses, "At least one component class must be specified");
		StartupStep registerComponentClass = this.getApplicationStartup().start("spring.context.component-classes.register")
				.tag("classes", () -> Arrays.toString(componentClasses));
		this.reader.register(componentClasses);    // 见AnnotatedBeanDefinitionReader代码10
		registerComponentClass.end();
	}



	/**
	 * Perform a scan within the specified base packages.
	 * <p>Note that {@link #refresh()} must be called in order for the context
	 * to fully process the new classes.
	 * @param basePackages the packages to scan for component classes
	 * @see #register(Class...)
	 * @see #refresh()
	 */
	@Override
	public void scan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		StartupStep scanPackages = this.getApplicationStartup().start("spring.context.base-packages.scan")
				.tag("packages", () -> Arrays.toString(basePackages));
		this.scanner.scan(basePackages);
		scanPackages.end();
	}


	//---------------------------------------------------------------------
	// Adapt superclass registerBean calls to AnnotatedBeanDefinitionReader
	//---------------------------------------------------------------------

	@Override
	public <T> void registerBean(@Nullable String beanName, Class<T> beanClass,
			@Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

		this.reader.registerBean(beanClass, beanName, supplier, customizers);
	}

}
