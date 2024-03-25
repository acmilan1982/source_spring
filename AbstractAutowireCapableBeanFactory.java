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

package org.springframework.beans.factory.support;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.AutowiredPropertyMarker;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.NativeDetector;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.util.StringUtils;

/**
 * Abstract bean factory superclass that implements default bean creation,
 * with the full capabilities specified by the {@link RootBeanDefinition} class.
 * Implements the {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * interface in addition to AbstractBeanFactory's {@link #createBean} method.
 *
 * <p>Provides bean creation (with constructor resolution), property population,
 * wiring (including autowiring), and initialization. Handles runtime bean
 * references, resolves managed collections, calls initialization methods, etc.
 * Supports autowiring constructors, properties by name, and properties by type.
 *
 * <p>The main template method to be implemented by subclasses is
 * {@link #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)},
 * used for autowiring by type. In case of a factory which is capable of searching
 * its bean definitions, matching beans will typically be implemented through such
 * a search. For other factory styles, simplified matching algorithms can be implemented.
 *
 * <p>Note that this class does <i>not</i> assume or implement bean definition
 * registry capabilities. See {@link DefaultListableBeanFactory} for an implementation
 * of the {@link org.springframework.beans.factory.ListableBeanFactory} and
 * {@link BeanDefinitionRegistry} interfaces, which represent the API and SPI
 * view of such a factory, respectively.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Chris Beams
 * @author Sam Brannen
 * @author Phillip Webb
 * @since 13.02.2004
 * @see RootBeanDefinition
 * @see DefaultListableBeanFactory
 * @see BeanDefinitionRegistry
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	/** Strategy for creating bean instances. */
	// CglibSubclassingInstantiationStrategy
	private InstantiationStrategy instantiationStrategy;

	/** Resolver strategy for method parameter names. */
	@Nullable
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	/** Whether to automatically try to resolve circular references between beans. */
	private boolean allowCircularReferences = true;

	/**
	 * Whether to resort to injecting a raw bean instance in case of circular reference,
	 * even if the injected bean eventually got wrapped.
	 */
	private boolean allowRawInjectionDespiteWrapping = false;

	/**
	 * Dependency types to ignore on dependency check and autowire, as Set of
	 * Class objects: for example, String. Default is none.
	 */
	private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();

	/**
	 * Dependency interfaces to ignore on dependency check and autowire, as Set of
	 * Class objects. By default, only the BeanFactory interface is ignored.
	 */
	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

	/**
	 * The name of the currently created bean, for implicit dependency registration
	 * on getBean etc invocations triggered from a user-specified Supplier callback.
	 */
	private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

	/** Cache of unfinished FactoryBean instances: FactoryBean name to BeanWrapper. */
	private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();

	/** Cache of candidate factory methods per factory class. */
	private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();

	/** Cache of filtered PropertyDescriptors: bean Class to PropertyDescriptor array. */
	private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache =
			new ConcurrentHashMap<>();


	/**
        Create a new AbstractAutowireCapableBeanFactory.
	 */
    // 构造方法
	public AbstractAutowireCapableBeanFactory() {
		super();
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
		if (NativeDetector.inNativeImage()) {
			this.instantiationStrategy = new SimpleInstantiationStrategy();
		}
		else {
			this.instantiationStrategy = new CglibSubclassingInstantiationStrategy();
		}
	}
 

	//---------------------------------------------------------------------
	// Implementation of relevant AbstractBeanFactory template methods
	//---------------------------------------------------------------------

	/**
		Central method of this class: creates a bean instance, populates the bean instance, applies post-processors, etc.
		这个类的中心方法：创建一个bean实例，填充bean实例，应用后处理器等。

		Specified by:
		createBean in class AbstractBeanFactory

		Parameters:
		   beanName - the name of the bean
		   mbd - the merged bean definition for the bean
		   args - explicit arguments to use for constructor or factory method invocation
		Returns:
		   a new instance of the bean
		Throws:
		   BeanCreationException - if the bean could not be created
	 */
	@Override
    // 代码10
	protected Object createBean(String beanName, 
								RootBeanDefinition mbd, 
								@Nullable Object[] args)
			throws BeanCreationException {

		if (logger.isTraceEnabled()) {
			logger.trace("Creating instance of bean '" + beanName + "'");
		}
		RootBeanDefinition mbdToUse = mbd;

		// Make sure bean class is actually resolved at this point, and
		// clone the bean definition in case of a dynamically resolved Class
		// which cannot be stored in the shared merged bean definition.
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			mbdToUse = new RootBeanDefinition(mbd);
			mbdToUse.setBeanClass(resolvedClass);
		}

		// Prepare method overrides.
		try {
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		try {
 
			/*
			 *    执行 InstantiationAwareBeanPostProcessor 的 postProcessBeforeInstantiation 方法
			 *    如果该方法返回非空的值，那么当前bean的创建过程立即结束，直接使用该返回值
			 * 	  如果返回空值，会继续bean的实例化
			 * 
             *    1. AutowiredAnnotationBeanPostProcessor继承了InstantiationAwareBeanPostProcessor，
			 *       未重写该方法，直接使用接口的默认实现，返回null
			 *    2. CommonAnnotationBeanPostProcessor继承了InstantiationAwareBeanPostProcessor，
			 *       未重写该方法，直接使用接口的默认实现，返回null
			 */
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);                     // 见代码15		

			// 如果InstantiationAwareBeanPostProcessor 的 postProcessBeforeInstantiation 方法返回非空值，当前处理中断，直接使用该返回值
			if (bean != null) {
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

		try {
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);                      // 见代码 30
			if (logger.isTraceEnabled()) {
				logger.trace("Finished creating instance of bean '" + beanName + "'");
			}
			return beanInstance;
		}
		catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			// A previously detected exception with proper bean creation context already,
			// or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}


	/**

		Apply before-instantiation post-processors, resolving whether there is a before-instantiation shortcut for the specified bean.
		Params:
		beanName – the name of the bean mbd – the bean definition for the bean
		Returns:
		the shortcut-determined bean instance, or null if none

	 */
	// 代码15
	@Nullable
	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			// Make sure bean class is actually resolved at this point.
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {   // 见代码16
				Class<?> targetType = determineTargetType(beanName, mbd);            // 见代码17
				if (targetType != null) {
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);   // 见代码23
					if (bean != null) {
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}


	// 代码16
	protected boolean hasInstantiationAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().instantiationAware.isEmpty();
	}


	// 代码17
	@Nullable
	protected Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType == null) {
			targetType = (mbd.getFactoryMethodName() != null ?
					getTypeForFactoryMethod(beanName, mbd, typesToMatch) :
					resolveBeanClass(mbd, beanName, typesToMatch));
			if (ObjectUtils.isEmpty(typesToMatch) || getTempClassLoader() == null) {
				mbd.resolvedTargetType = targetType;
			}
		}
		return targetType;
	}


	/**

		Apply InstantiationAwareBeanPostProcessors to the specified bean definition (by class and name), 
		invoking their postProcessBeforeInstantiation methods.

		Any returned object will be used as the bean instead of actually instantiating the target bean. 
		A null return value from the post-processor will result in the target bean being instantiated.
		Params:
		  beanClass – the class of the bean to be instantiated beanName – the name of the bean
		Returns:
		  the bean object to use instead of a default instance of the target bean, or null
		See Also:
		  InstantiationAwareBeanPostProcessor.postProcessBeforeInstantiation


		执行InstantiationAwareBeanPostProcessors的postProcessBeforeInstantiation方法
		一旦该方法返回了非空的值，真正实例化bean的操作将会被短路(不再被执行)
		如果返回空值，会继续bean的实例化

	 */
	// 代码23
	@Nullable
	protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
		for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
			Object result = bp.postProcessBeforeInstantiation(beanClass, beanName);
			if (result != null) {
				return result;
			}
		}
		return null;
	}


	/**
		Actually create the specified bean. 
		Pre-creation processing has already happened at this point, e.g. checking postProcessBeforeInstantiation callbacks.
		Differentiates between default bean instantiation, use of a factory method, and autowiring a constructor.
		Params:
		beanName – the name of the bean mbd – the merged bean definition for the bean args – explicit arguments to use for constructor or factory method invocation
		Returns:
		a new instance of the bean
		Throws:
		BeanCreationException – if the bean could not be created
		See Also:
		instantiateBean, instantiateUsingFactoryMethod, autowireConstructor
	 */
	// 代码30
	@Override
	protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		// Instantiate the bean.
		BeanWrapper instanceWrapper = null;
		if (mbd.isSingleton()) {
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		if (instanceWrapper == null) {
			// 实例化bean，并包装成 BeanWrapper 
			instanceWrapper = createBeanInstance(beanName, mbd, args);           // 见代码31
		}

		Object bean = instanceWrapper.getWrappedInstance();
		Class<?> beanType = instanceWrapper.getWrappedClass();
		if (beanType != NullBean.class) {
			mbd.resolvedTargetType = beanType;
		}

		// Allow post-processors to modify the merged bean definition.
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					/*
					      执行所有 MergedBeanDefinitionPostProcessor 的 postProcessMergedBeanDefinition 方法
					      1. AutowiredAnnotationBeanPostProcessor 实现了  MergedBeanDefinitionPostProcessor接口，见AutowiredAnnotationBeanPostProcessor代码5
						     遍历整个类的继承体系，每一个被 @Autowired，@Value的属性或方法，封装成 InjectedElement，
		                     所有的InjectedElement，封装成 InjectionMetadata
							 AutowiredAnnotationBeanPostProcessor持有每个类对应的 InjectionMetadata
					 */
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);           // 见代码39
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				mbd.postProcessed = true;
			}
		}

		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
		}



		// 开始初始化 bean
		// Initialize the bean instance.
		Object exposedObject = bean;
		try {
			populateBean(beanName, mbd, instanceWrapper);                      // 见代码 40
			/*
				这里的处理大致分为4个部分,按照顺序：
				step1: 检查bean是否实现了一些aware接口：
						如果bean实现了BeanNameAware，BeanClassLoaderAware，BeanFactoryAware中的一些接口
						则执行这些接口定义的方法，用以向bean中注入beanName，ClassLoader，BeanFactory
						
				step2: 执行BeanPostProcessor的postProcessBeforeInitialization方法
				
				step3: 1.如果bean实现了org.springframework.beans.factory.InitializingBean接口，执行其afterPropertiesSet()方法
					2.如果bean自定义了init-method方法，执行该方法
						
				step4: 执行BeanPostProcessor的postProcessAfterInitialization方法
					自动代理机制会在这一步创建代理对象   

			*/			
			exposedObject = initializeBean(beanName, exposedObject, mbd);      // 见代码60
		}
		catch (Throwable ex) {
			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
				throw (BeanCreationException) ex;
			}
			else {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
			}
		}

		if (earlySingletonExposure) {
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				if (exposedObject == bean) {
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					if (!actualDependentBeans.isEmpty()) {
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

		// Register bean as disposable.
		try {
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		return exposedObject;
	}





	/**
		Create a new instance for the specified bean, 
		using an appropriate instantiation strategy: factory method, constructor autowiring, or simple instantiation.

		Params:
		  beanName – the name of the bean mbd – the bean definition for the bean args – explicit arguments to use for constructor or factory method invocation
		Returns:
		  a BeanWrapper for the new instance
		See Also:
		  obtainFromSupplier, instantiateUsingFactoryMethod, autowireConstructor, instantiateBean

		实例化bean，并包装成 BeanWrapper 
	 */
	// 代码31
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// 需要实例化的bean的 Class
		// Make sure bean class is actually resolved at this point.
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}

		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			return obtainFromSupplier(instanceSupplier, beanName);
		}

		//被@Bean的方法，在这里被处理
		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(beanName, mbd, args);    // 见代码33
		}

		// Shortcut when re-creating the same bean...
		boolean resolved = false;
		boolean autowireNecessary = false;
		if (args == null) {
			synchronized (mbd.constructorArgumentLock) {
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		if (resolved) {
			if (autowireNecessary) {
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				return instantiateBean(beanName, mbd);
			}
		}

		// Candidate constructors for autowiring?
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// Preferred constructors for default construction?
		ctors = mbd.getPreferredConstructors();
		if (ctors != null) {
			return autowireConstructor(beanName, mbd, ctors, null);
		}

		// No special handling: simply use no-arg constructor.
		return instantiateBean(beanName, mbd);        // 见代码30
	}



	// 代码33
	protected BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
	}


	/**
		Instantiate the given bean using its default constructor.
		Params:
		  beanName – the name of the bean 
		  mbd – the bean definition for the bean
		Returns:
		  a BeanWrapper for the new instance

	    实例化bean，并包装成 BeanWrapper
	 */
	// 代码30
	protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
		try {
			Object beanInstance;
			if (System.getSecurityManager() != null) {
				beanInstance = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(mbd, beanName, this),
						getAccessControlContext());
			}
			else {
				beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);   // instantiate方法 见 SimpleInstantiationStrategy 代码1
			}

			// 实例化之后的 bean，包装成 BeanWrapper
			BeanWrapper bw = new BeanWrapperImpl(beanInstance);         // 见 BeanWrapperImpl 构造方法3
			initBeanWrapper(bw);
			return bw;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
		}
	}


	/**
		Return the instantiation strategy to use for creating bean instances.
	 */
	// 代码31
	protected InstantiationStrategy getInstantiationStrategy() {
		return this.instantiationStrategy;
	}




	/**
		Apply MergedBeanDefinitionPostProcessors to the specified bean definition, invoking their postProcessMergedBeanDefinition methods.
		Params:
		mbd – the merged bean definition for the bean beanType – the actual type of the managed bean instance beanName – the name of the bean
		See Also:
		MergedBeanDefinitionPostProcessor.postProcessMergedBeanDefinition
	 */
	// 代码39
	protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
		for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
			processor.postProcessMergedBeanDefinition(mbd, beanType, beanName);
		}
	}

	/**
		Populate the bean instance in the given BeanWrapper with the property values from the bean definition.

		Params:
		  beanName – the name of the bean mbd – the bean definition for the bean bw – the BeanWrapper with bean instance
	 */
	// 代码40
	protected void populateBean(String beanName,
								RootBeanDefinition mbd, 
								@Nullable BeanWrapper bw) {
		if (bw == null) {
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				// Skip property population phase for null instance.
				return;
			}
		}

		/*
		     执行 InstantiationAwareBeanPostProcessors 的 postProcessAfterInstantiation方法
			 如果返回false，依赖注入步骤会被skip


		 */
		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				if (!bp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
					return;
				}
			}
		}

		// PropertyValues通常来源于xml中的配置
		// 基于注解的开发，PropertyValues一般都是null
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

		int resolvedAutowireMode = mbd.getResolvedAutowireMode();
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
			// Add property values based on autowire by name if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
				autowireByName(beanName, mbd, bw, newPvs);
			}
			// Add property values based on autowire by type if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}
			pvs = newPvs;
		}



		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

		PropertyDescriptor[] filteredPds = null;
		if (hasInstAwareBpps) {
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}

			/*
			      执行 InstantiationAwareBeanPostProcessor 的 postProcessProperties 方法
                  该方法可以对 PropertyValues 做进一步的处理
				  AutowiredAnnotationBeanPostProcessor 重写了该方法，会处理 @Autowired，@Value 的依赖注入
			  
			 */
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				PropertyValues pvsToUse = bp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);   // 见 AutowiredAnnotationBeanPostProcessor 代码20
				if (pvsToUse == null) {
					if (filteredPds == null) {
						filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
					}

					/*
						执行 InstantiationAwareBeanPostProcessor 的 postProcessPropertyValues 方法
						该方法可以对 PropertyValues 做进一步的处理
					
					*/					
					pvsToUse = bp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
					if (pvsToUse == null) {
						return;
					}
				}
				pvs = pvsToUse;
			}
		}
		if (needsDepCheck) {
			if (filteredPds == null) {
				filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			}
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}

		if (pvs != null) {
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}





    /**
		Initialize the given bean instance, applying factory callbacks as well as init methods and bean post processors.
		Called from createBean for traditionally defined beans, and from initializeBean for existing bean instances.
		Params:
			beanName – the bean name in the factory (for debugging purposes) 
			bean – the new bean instance we may need to initialize 
			mbd – the bean definition that the bean was created with (can also be null, if given an existing bean instance)
		Returns:
		    the initialized bean instance (potentially wrapped)
		See Also:
		    BeanNameAware, 
			BeanClassLoaderAware, 
			BeanFactoryAware, 
			applyBeanPostProcessorsBeforeInitialization, 
			invokeInitMethods, 
			applyBeanPostProcessorsAfterInitialization

		这里的处理大致分为4个部分,按照顺序：
		step1: 检查bean是否实现了一些aware接口：
				如果bean实现了BeanNameAware，BeanClassLoaderAware，BeanFactoryAware中的一些接口
				则执行这些接口定义的方法，用以向bean中注入beanName，ClassLoader，BeanFactory
				
		step2: 执行BeanPostProcessor的postProcessBeforeInitialization方法
		
		step3: 1.如果bean实现了org.springframework.beans.factory.InitializingBean接口，执行其afterPropertiesSet()方法
			   2.如果bean自定义了init-method方法，执行该方法
				
		step4: 执行BeanPostProcessor的postProcessAfterInitialization方法
			   自动代理机制会在这一步创建代理对象   
	 */
	// 代码60
	protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareMethods(beanName, bean);
				return null;
			}, getAccessControlContext());
		}
		else {
			/*
				step1. 检查bean是否实现了一些aware接口：
					   如果bean实现了BeanNameAware，BeanClassLoaderAware，BeanFactoryAware中的一些接口
					   则执行这些接口定义的方法，用以向bean中注入beanName，ClassLoader，BeanFactory
			 * 
			 */
			invokeAwareMethods(beanName, bean);                        // 见代码61
		}



		Object wrappedBean = bean;
		/*
			step2. 执行BeanPostProcessor的postProcessBeforeInitialization方法  
		*/	
		if (mbd == null || !mbd.isSynthetic()) {
	
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);    // 见代码62
		}

		try {
			/*
				step3: 1. 如果bean实现了org.springframework.beans.factory.InitializingBean接口，执行其afterPropertiesSet()方法
					   2. 如果bean自定义了init-method方法，执行该方法								    
			*/			
			invokeInitMethods(beanName, wrappedBean, mbd);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed", ex);
		}

		/*
				step4: 执行BeanPostProcessor的postProcessAfterInitialization方法
					自动代理机制会在这一步创建代理对象
							
																		BeanPostProcessor
																				|
																InstantiationAwareBeanPostProcessor
																				|
															SmartInstantiationAwareBeanPostProcessor
																				|
																		AbstractAutoProxyCreator
																				|
																	AbstractAdvisorAutoProxyCreator
								|                              |                                             |
				DefaultAdvisorAutoProxyCreator    InfrastructureAdvisorAutoProxyCreator    AspectJAwareAdvisorAutoProxyCreator
																																					| 
																																		AnnotationAwareAspectJAutoProxyCreator   
					
				DefaultAdvisorAutoProxyCreator,InfrastructureAdvisorAutoProxyCreator,AnnotationAwareAspectJAutoProxyCreator都是BeanPostProcessor对象
				因此，也实现了postProcessAfterInitialization方法
				
				当程序运行到这里时，目标对象已经创建完成，并且已经执行了目标对象的init_method方法等(完整初始化)
				postProcessAfterInitialization方法实际上会创建目标对象的代理对象							  
				postProcessAfterInitialization实现定义在他们的公共父类AbstractAutoProxyCreator中，见该类代码10						    
		*/			
		if (mbd == null || !mbd.isSynthetic()) {
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}

		return wrappedBean;
	}

	/*
		step1. 检查bean是否实现了一些aware接口：
				如果bean实现了BeanNameAware，BeanClassLoaderAware，BeanFactoryAware中的一些接口
				则执行这些接口定义的方法，用以向bean中注入beanName，ClassLoader，BeanFactory
	*/						
	//代码61
	private void invokeAwareMethods(String beanName, Object bean) {
		if (bean instanceof Aware) {
			if (bean instanceof BeanNameAware) {
				((BeanNameAware) bean).setBeanName(beanName);
			}
			if (bean instanceof BeanClassLoaderAware) {
				ClassLoader bcl = getBeanClassLoader();
				if (bcl != null) {
					((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
				}
			}
			if (bean instanceof BeanFactoryAware) {
				((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
			}
		}
	}	

	

    // 代码62
	@Override
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			Object current = processor.postProcessBeforeInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}
 
    /**
		Give a bean a chance to react now all its properties are set, and a chance to know about its owning bean factory (this object). 
		This means checking whether the bean implements InitializingBean or defines a custom init method, 
		and invoking the necessary callback(s) if it does.

		Params:
		  beanName – the bean name in the factory (for debugging purposes) bean – the new bean instance we may need to initialize mbd – the merged bean definition that the bean was created with (can also be null, if given an existing bean instance)
		Throws:
		  Throwable – if thrown by init methods or by the invocation process
		See Also:
		  invokeCustomInitMethod 

	 */
    // 代码63
	protected void invokeInitMethods(String beanName, Object bean, @Nullable RootBeanDefinition mbd)
			throws Throwable {

		boolean isInitializingBean = (bean instanceof InitializingBean);
		// 判断当前bean是否实现了 		 
		boolean isInitializingBean = (bean instanceof InitializingBean);
		if (isInitializingBean && (mbd == null || !mbd.hasAnyExternallyManagedInitMethod("afterPropertiesSet"))) {
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
			}
			if (System.getSecurityManager() != null) {
				try {
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						((InitializingBean) bean).afterPropertiesSet();
						return null;
					}, getAccessControlContext());
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				 // 执行 InitializingBean 接口 的 afterPropertiesSet()方法
				((InitializingBean) bean).afterPropertiesSet();
			}
		}

		if (mbd != null && bean.getClass() != NullBean.class) {
			String initMethodName = mbd.getInitMethodName();
			if (StringUtils.hasLength(initMethodName) &&
					!(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
					!mbd.hasAnyExternallyManagedInitMethod(initMethodName)) {
				// 执行bean自定义的初始化方法		
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}	

    /**
		Invoke the specified custom init method on the given bean. Called by invokeInitMethods.
		Can be overridden in subclasses for custom resolution of init methods with arguments.

		See Also:
		  invokeInitMethods

	 */
    // 代码64
	protected void invokeCustomInitMethod(String beanName, Object bean, RootBeanDefinition mbd)
			throws Throwable {

		String initMethodName = mbd.getInitMethodName();
		Assert.state(initMethodName != null, "No init method set");
		Method initMethod = (mbd.isNonPublicAccessAllowed() ?
				BeanUtils.findMethod(bean.getClass(), initMethodName) :
				ClassUtils.getMethodIfAvailable(bean.getClass(), initMethodName));

		if (initMethod == null) {
			if (mbd.isEnforceInitMethod()) {
				throw new BeanDefinitionValidationException("Could not find an init method named '" +
						initMethodName + "' on bean with name '" + beanName + "'");
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("No default init method named '" + initMethodName +
							"' found on bean with name '" + beanName + "'");
				}
				// Ignore non-existent default lifecycle methods.
				return;
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");
		}
		Method methodToInvoke = ClassUtils.getInterfaceMethodIfPossible(initMethod, bean.getClass());

		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(methodToInvoke);
				return null;
			});
			try {
				AccessController.doPrivileged((PrivilegedExceptionAction<Object>)
						() -> methodToInvoke.invoke(bean), getAccessControlContext());
			}
			catch (PrivilegedActionException pae) {
				InvocationTargetException ex = (InvocationTargetException) pae.getException();
				throw ex.getTargetException();
			}
		}
		else {
			try {
				ReflectionUtils.makeAccessible(methodToInvoke);
				methodToInvoke.invoke(bean);
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}

	/**
		Description copied from interface: AutowireCapableBeanFactory
		Apply BeanPostProcessors to the given existing bean instance, invoking their postProcessAfterInitialization methods. 
		The returned bean instance may be a wrapper around the original.
		
		Specified by:
		  applyBeanPostProcessorsAfterInitialization in interface AutowireCapableBeanFactory
		Parameters:
		  existingBean - the existing bean instance
		  beanName - the name of the bean, to be passed to it if necessary (only passed to BeanPostProcessors; 
		             can follow the AutowireCapableBeanFactory.ORIGINAL_INSTANCE_SUFFIX convention in order to enforce the given instance to be returned, i.e. no proxies etc)
		Returns:
		  the bean instance to use, either the original or a wrapped one
		Throws:
		  BeansException - if any post-processing failed
		See Also:
		  BeanPostProcessor.postProcessAfterInitialization(java.lang.Object, java.lang.String), AutowireCapableBeanFactory.ORIGINAL_INSTANCE_SUFFIX

	    在bean初始化之后，比如：existingBean为 InstantiationAwareBeanPostProcessor 对象创建的一个对象(类型不限)
		当前方法调用已注册BeanPostProcessor对象的postProcessAfterInitialization方法

	 */
	// 代码65
	@Override
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, 
	                                                         String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {                      // getBeanPostProcessors() 获取当前工厂的所有 BeanPostProcessor，见父类 AbstractBeanFactory 代码50
			/*
			*   关于 aop 的 BeanPostProcessor，为 InfrastructureAdvisorAutoProxyCreator
			*/
			Object current = processor.postProcessAfterInitialization(result, beanName);   //  关于 aop ，见父类 AbstractAutoProxyCreator 代码5
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}	
}
