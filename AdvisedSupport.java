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

package org.springframework.aop.framework;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aopalliance.aop.Advice;

import org.springframework.aop.Advisor;
import org.springframework.aop.DynamicIntroductionAdvice;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionInfo;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.target.EmptyTargetSource;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Base class for AOP proxy configuration managers.
 * These are not themselves AOP proxies, but subclasses of this class are
 * normally factories from which AOP proxy instances are obtained directly.
 *
 * <p>This class frees subclasses of the housekeeping of Advices
 * and Advisors, but doesn't actually implement proxy creation
 * methods, which are provided by subclasses.
 *
 * <p>This class is serializable; subclasses need not be.
 * This class is used to hold snapshots of proxies.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.aop.framework.AopProxy
 */
public class AdvisedSupport extends ProxyConfig implements Advised {

	/** use serialVersionUID from Spring 2.0 for interoperability. */
	private static final long serialVersionUID = 2651364800145442165L;


	/**
	 * Canonical TargetSource when there's no target, and behavior is
	 * supplied by the advisors.
	 */
	public static final TargetSource EMPTY_TARGET_SOURCE = EmptyTargetSource.INSTANCE;


	// 目标对象
	/** Package-protected to allow direct access for efficiency. */
	TargetSource targetSource = EMPTY_TARGET_SOURCE;

	/** Whether the Advisors are already filtered for the specific target class. */
	private boolean preFiltered = false;

	/** The AdvisorChainFactory to use. */
	AdvisorChainFactory advisorChainFactory = new DefaultAdvisorChainFactory();

	/** Cache with Method as key and advisor chain List as value. */
	private transient Map<MethodCacheKey, List<Object>> methodCache;

	// 需要被代理的所有接口，可能包括目标对象的所有接口
	/**
	 * Interfaces to be implemented by the proxy. Held in List to keep the order
	 * of registration, to create JDK proxy with specified order of interfaces.
	 */
	private List<Class<?>> interfaces = new ArrayList<>();

	// 保存所有的Advisor实例，如果提供的是Advice，会被封装成DefaultPointcutAdvisor，表示匹配所有的Pointcut
	/**
	 * List of Advisors. 
	 * 
	 * If an Advice is added, it will be wrapped in an Advisor before being added to this List
	 */
	// 
	private List<Advisor> advisors = new ArrayList<>();


	/**
        No-arg constructor for use as a JavaBean.
	 */
	// 构造方法1
	public AdvisedSupport() {
		this.methodCache = new ConcurrentHashMap<>(32);
	}

	/**
	 * Create a AdvisedSupport instance with the given parameters.
	 * @param interfaces the proxied interfaces
	 */
	public AdvisedSupport(Class<?>... interfaces) {
		this();
		setInterfaces(interfaces);
	}



    /**
		Set the given object as target. Will create a SingletonTargetSource for the object.
		See Also:
		setTargetSource, SingletonTargetSource

		参数给定的对象，视为目标对象，创建对应的SingletonTargetSource

    */
    // 代码1
	public void setTarget(Object target) {
		setTargetSource(new SingletonTargetSource(target));        // setTargetSource方法见代码2   
	}




    /**
		Description copied from interface: Advised

		Change the TargetSource used by this Advised object.
		Only works if the configuration isn't frozen.
		
		Specified by:
		  setTargetSource in interface Advised
		Parameters:

		  targetSource - new TargetSource to us

    */
    // 代码2
	@Override
	public void setTargetSource(@Nullable TargetSource targetSource) {
		this.targetSource = (targetSource != null ? targetSource : EMPTY_TARGET_SOURCE);
	}



    /**
		Set the interfaces to be proxied.
    */
    // 代码3
	public void setInterfaces(Class<?>... interfaces) {
		Assert.notNull(interfaces, "Interfaces must not be null");
		this.interfaces.clear();
		for (Class<?> ifc : interfaces) {
			addInterface(ifc);
		}
	}

    /**
		Add a new proxied interface.
		Params:
		intf – the additional interface to proxy
    */
    // 代码4
	public void addInterface(Class<?> intf) {
		Assert.notNull(intf, "Interface must not be null");
		if (!intf.isInterface()) {
			throw new IllegalArgumentException("[" + intf.getName() + "] is not an interface");
		}
		if (!this.interfaces.contains(intf)) {
			this.interfaces.add(intf);
			adviceChanged();
		}
	}
	
	


    /**
    
		Description copied from interface: Adviseds
		Add the given AOP Alliance advice to the tail of the advice (interceptor) chain.
		This will be wrapped in a DefaultPointcutAdvisor with a pointcut that always applies, and returned from the getAdvisors() method in this wrapped form.

		Note that the given advice will apply to all invocations on the proxy, even to the toString() method! 
		Use appropriate advice implementations or specify appropriate pointcuts to apply to a narrower set of methods.

		Specified by:
		  addAdvice in interface Advised
		Parameters:
		  advice - the advice to add to the tail of the chain
		Throws:
		  AopConfigException - in case of invalid advice
		See Also:
		  Advised.addAdvice(int, Advice), DefaultPointcutAdvisor

	    
		Advice: org.aopalliance.aop.Advice
		参数指定的类型，是 org.aopalliance.aop.Advice 对象，该接口是个标记接口，未定义任何方法

        参数指定的Advice对象，包装成 DefaultPointcutAdvisor，加入 advisors 队尾
    */
    // 代码5
	@Override
	public void addAdvice(Advice advice) throws AopConfigException {
		int pos = this.advisors.size();     //  当前类的属性: private List<Advisor> advisors = new ArrayList<>(); 持有所有Advisor
		addAdvice(pos, advice);             // 见代码6
	}

	/**
	 * Cannot add introductions this way unless the advice implements IntroductionInfo.
	 */
	// 代码6
	@Override
	public void addAdvice(int pos, Advice advice) throws AopConfigException {
		Assert.notNull(advice, "Advice must not be null");
		if (advice instanceof IntroductionInfo) {
			// We don't need an IntroductionAdvisor for this kind of introduction:
			// It's fully self-describing.
			addAdvisor(pos, new DefaultIntroductionAdvisor(advice, (IntroductionInfo) advice));
		}
		else if (advice instanceof DynamicIntroductionAdvice) {
			// We need an IntroductionAdvisor for this kind of introduction.
			throw new AopConfigException("DynamicIntroductionAdvice may only be added as part of IntroductionAdvisor");
		}
		else {
			addAdvisor(pos, new DefaultPointcutAdvisor(advice));  // 见代码7
		}
	}


	// 代码7
	@Override
	public void addAdvisor(int pos, Advisor advisor) throws AopConfigException {
		if (advisor instanceof IntroductionAdvisor) {
			validateIntroductionAdvisor((IntroductionAdvisor) advisor);
		}
		addAdvisorInternal(pos, advisor);  // 见代码8
	}

    // 代码8
	private void addAdvisorInternal(int pos, Advisor advisor) throws AopConfigException {
		Assert.notNull(advisor, "Advisor must not be null");
		if (isFrozen()) {
			throw new AopConfigException("Cannot add advisor: Configuration is frozen.");
		}
		if (pos > this.advisors.size()) {
			throw new IllegalArgumentException(
					"Illegal position " + pos + " in advisor list with size " + this.advisors.size());
		}
		this.advisors.add(pos, advisor);
		adviceChanged();  // 见代码9
	}


    
	/**
	 * Invoked when advice has changed.
	 */
	// 代码9
	protected void adviceChanged() {
		this.methodCache.clear();
	}







 


    /**
		Determine a list of org.aopalliance.intercept.MethodInterceptor objects for the given method, based on this configuration.

		Params:
		  method – the proxied method 
		  targetClass – the target class
		Returns:
		  a List of MethodInterceptors (may also include InterceptorAndDynamicMethodMatchers)		
		  
		根据当前配置，确定作用于某个方法上的advice链表,也就是spring提供的各种类型的advice接口的实现类
		在第一次找到方法对应的advice链表以后，会缓存该链表					
		
		
		Cglib2AopProxy.DynamicAdvisedInterceptor在执行时会调用当前方法：
		遍历AdvisedSupport提供的所有Advisor对象，根据其Pointcut与当前方法进行静态或动态匹配
		匹配成功的，把非org.aopalliance.intercept.MethodInterceptor类型的advice
		都转换成该类型的advice,以便统一用invoke方法调用源advice中的方法
		最终返回MethodInterceptor对象组成的List
		
		如果需要进行动态匹配，此处未进行匹配，而是把该MethodInterceptor封装成InterceptorAndDynamicMethodMatcher对象，加入List:
			new InterceptorAndDynamicMethodMatcher(interceptor, mm)
		InterceptorAndDynamicMethodMatcher持有MethodMatcher，稍后可进行动态匹配
		
		出于缓存chain目的，如果某个advisor需要进行动态匹配，那么对于同一个方法不同参数的调用，可能匹配可能不匹配，这样就无法缓存整个chain
		解决方式为：先不进行动态匹配，而是封装advice和MethodMatcher为一个InterceptorAndDynamicMethodMatcher
		这样，就可以缓存整个chain，而在递归执行MethodInterceptor时，再进行动态匹配			  

    */
    // 代码10
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Method method, 
	                                                                @Nullable Class<?> targetClass) {
		MethodCacheKey cacheKey = new MethodCacheKey(method);
		List<Object> cached = this.methodCache.get(cacheKey);
		if (cached == null) {
			/*			    
				寻找当前Method对应的Interceptor链表
				advisorChainFactory为当前类的实例属性：AdvisorChainFactory advisorChainFactory = new DefaultAdvisorChainFactory();
			*/	
			cached = this.advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice(this,             // 见 DefaultAdvisorChainFactory 代码10
																						  method, 
																						  targetClass);
			this.methodCache.put(cacheKey, cached);
		}
		return cached;
	}



	/**
	 * Call this method on a new instance created by the no-arg constructor
	 * to create an independent copy of the configuration from the given object.
	 * @param other the AdvisedSupport object to copy configuration from
	 */
	protected void copyConfigurationFrom(AdvisedSupport other) {
		copyConfigurationFrom(other, other.targetSource, new ArrayList<>(other.advisors));
	}

	/**
	 * Copy the AOP configuration from the given AdvisedSupport object,
	 * but allow substitution of a fresh TargetSource and a given interceptor chain.
	 * @param other the AdvisedSupport object to take proxy configuration from
	 * @param targetSource the new TargetSource
	 * @param advisors the Advisors for the chain
	 */
	protected void copyConfigurationFrom(AdvisedSupport other, TargetSource targetSource, List<Advisor> advisors) {
		copyFrom(other);
		this.targetSource = targetSource;
		this.advisorChainFactory = other.advisorChainFactory;
		this.interfaces = new ArrayList<>(other.interfaces);
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor) {
				validateIntroductionAdvisor((IntroductionAdvisor) advisor);
			}
			Assert.notNull(advisor, "Advisor must not be null");
			this.advisors.add(advisor);
		}
		adviceChanged();
	}

	/**
	 * Build a configuration-only copy of this AdvisedSupport,
	 * replacing the TargetSource.
	 */
	AdvisedSupport getConfigurationOnlyCopy() {
		AdvisedSupport copy = new AdvisedSupport();
		copy.copyFrom(this);
		copy.targetSource = EmptyTargetSource.forClass(getTargetClass(), getTargetSource().isStatic());
		copy.advisorChainFactory = this.advisorChainFactory;
		copy.interfaces = new ArrayList<>(this.interfaces);
		copy.advisors = new ArrayList<>(this.advisors);
		return copy;
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.methodCache = new ConcurrentHashMap<>(32);
	}

	@Override
	public String toProxyConfigString() {
		return toString();
	}

	/**
	 * For debugging/diagnostic use.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getName());
		sb.append(": ").append(this.interfaces.size()).append(" interfaces ");
		sb.append(ClassUtils.classNamesToString(this.interfaces)).append("; ");
		sb.append(this.advisors.size()).append(" advisors ");
		sb.append(this.advisors).append("; ");
		sb.append("targetSource [").append(this.targetSource).append("]; ");
		sb.append(super.toString());
		return sb.toString();
	}


	/**
	 * Simple wrapper class around a Method. Used as the key when
	 * caching methods, for efficient equals and hashCode comparisons.
	 */
	private static final class MethodCacheKey implements Comparable<MethodCacheKey> {

		private final Method method;

		private final int hashCode;

		public MethodCacheKey(Method method) {
			this.method = method;
			this.hashCode = method.hashCode();
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof MethodCacheKey &&
					this.method == ((MethodCacheKey) other).method));
		}

		@Override
		public int hashCode() {
			return this.hashCode;
		}

		@Override
		public String toString() {
			return this.method.toString();
		}

		@Override
		public int compareTo(MethodCacheKey other) {
			int result = this.method.getName().compareTo(other.method.getName());
			if (result == 0) {
				result = this.method.toString().compareTo(other.method.toString());
			}
			return result;
		}
	}

}
