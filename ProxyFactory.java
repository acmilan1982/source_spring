/*
 * Copyright 2002-2016 the original author or authors.
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

import org.aopalliance.intercept.Interceptor;

import org.springframework.aop.TargetSource;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Factory for AOP proxies for programmatic use, rather than via declarative
 * setup in a bean factory. This class provides a simple way of obtaining
 * and configuring AOP proxy instances in custom user code.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 14.03.2003
 */
@SuppressWarnings("serial")
public class ProxyFactory extends ProxyCreatorSupport {

	/**
        Create a new ProxyFactory.
	 */
	// 构造方法1
	public ProxyFactory() {
	}

	/**
		Create a new ProxyFactory.
		Will proxy all interfaces that the given target implements.
		Params:
		target – the target object to be proxied

		创建代理对象时，可以直接给目标对象
		代理对象会实现目标对象的所有接口
	 */
	// 构造方法1
	public ProxyFactory(Object target) {
		// 设置目标对象
		setTarget(target);                                       // 见父类 AdvisedSupport 代码1
		// 设置目标对象的所有接口，这些接口需要被代理实现
		setInterfaces(ClassUtils.getAllInterfaces(target));      // setInterfaces见父类 AdvisedSupport 代码3
	}

	/**
	 * Create a new ProxyFactory.
	 * <p>No target, only interfaces. Must add interceptors.
	 * @param proxyInterfaces the interfaces that the proxy should implement
	 */
	public ProxyFactory(Class<?>... proxyInterfaces) {
		setInterfaces(proxyInterfaces);
	}

	/**
	 * Create a new ProxyFactory for the given interface and interceptor.
	 * <p>Convenience method for creating a proxy for a single interceptor,
	 * assuming that the interceptor handles all calls itself rather than
	 * delegating to a target, like in the case of remoting proxies.
	 * @param proxyInterface the interface that the proxy should implement
	 * @param interceptor the interceptor that the proxy should invoke
	 */
	public ProxyFactory(Class<?> proxyInterface, Interceptor interceptor) {
		addInterface(proxyInterface);
		addAdvice(interceptor);
	}

	/**
	 * Create a ProxyFactory for the specified {@code TargetSource},
	 * making the proxy implement the specified interface.
	 * @param proxyInterface the interface that the proxy should implement
	 * @param targetSource the TargetSource that the proxy should invoke
	 */
	public ProxyFactory(Class<?> proxyInterface, TargetSource targetSource) {
		addInterface(proxyInterface);
		setTargetSource(targetSource);
	}









 
    /**
        Create a new proxy according to the settings in this factory.
        Can be called repeatedly. Effect will vary if we've added or removed interfaces. Can add and remove interceptors.
        Uses the given class loader (if necessary for proxy creation).

        Params:
          classLoader – the class loader to create the proxy with (or null for the low-level proxy facility's default)
        Returns:
       
          the proxy object


	    根据当前ProxyFactory的设置，创建代理对象
    */
    // 代码5
	public Object getProxy(@Nullable ClassLoader classLoader) {

	    /*
		 *   createAopProxy()方法创建AopProxy对象，该对象用来创建具体的代理类
		 * 
		 */
		return createAopProxy().getProxy(classLoader);  // createAopProxy()方法 见父类代码5
	}


	/**
	 * Create a new proxy for the given interface and interceptor.
	 * <p>Convenience method for creating a proxy for a single interceptor,
	 * assuming that the interceptor handles all calls itself rather than
	 * delegating to a target, like in the case of remoting proxies.
	 * @param proxyInterface the interface that the proxy should implement
	 * @param interceptor the interceptor that the proxy should invoke
	 * @return the proxy object
	 * @see #ProxyFactory(Class, org.aopalliance.intercept.Interceptor)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getProxy(Class<T> proxyInterface, Interceptor interceptor) {
		return (T) new ProxyFactory(proxyInterface, interceptor).getProxy();
	}

	/**
	 * Create a proxy for the specified {@code TargetSource},
	 * implementing the specified interface.
	 * @param proxyInterface the interface that the proxy should implement
	 * @param targetSource the TargetSource that the proxy should invoke
	 * @return the proxy object
	 * @see #ProxyFactory(Class, org.springframework.aop.TargetSource)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getProxy(Class<T> proxyInterface, TargetSource targetSource) {
		return (T) new ProxyFactory(proxyInterface, targetSource).getProxy();
	}

	/**
	 * Create a proxy for the specified {@code TargetSource} that extends
	 * the target class of the {@code TargetSource}.
	 * @param targetSource the TargetSource that the proxy should invoke
	 * @return the proxy object
	 */
	public static Object getProxy(TargetSource targetSource) {
		if (targetSource.getTargetClass() == null) {
			throw new IllegalArgumentException("Cannot create class proxy for TargetSource with null target class");
		}
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTargetSource(targetSource);
		proxyFactory.setProxyTargetClass(true);
		return proxyFactory.getProxy();
	}

}
