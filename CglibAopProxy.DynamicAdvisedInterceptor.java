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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Advisor;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.cglib.core.ClassLoaderAwareGeneratorStrategy;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.CallbackFilter;
import org.springframework.cglib.proxy.Dispatcher;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.core.KotlinDetector;
import org.springframework.core.SmartClassLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * CGLIB-based {@link AopProxy} implementation for the Spring AOP framework.
 *
 * <p>Objects of this type should be obtained through proxy factories,
 * configured by an {@link AdvisedSupport} object. This class is internal
 * to Spring's AOP framework and need not be used directly by client code.
 *
 * <p>{@link DefaultAopProxyFactory} will automatically create CGLIB-based
 * proxies if necessary, for example in case of proxying a target class
 * (see the {@link DefaultAopProxyFactory attendant javadoc} for details).
 *
 * <p>Proxies created using this class are thread-safe if the underlying
 * (target) class is thread-safe.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Chris Beams
 * @author Dave Syer
 * @see org.springframework.cglib.proxy.Enhancer
 * @see AdvisedSupport#setProxyTargetClass
 * @see DefaultAopProxyFactory
 */
@SuppressWarnings("serial")
class CglibAopProxy implements AopProxy, Serializable {








	/**
	 * General purpose AOP callback. Used when the target is dynamic or when the
	 * proxy is not frozen.
	 */
	private static class DynamicAdvisedInterceptor implements MethodInterceptor, Serializable {

		// 该对象包括了构造一个代理对象所需的全部数据，如advice,目标对象等等
		// 代理对象构造完成以后，会调用该对象的advisor等完成方法的拦截等操作		
		private final AdvisedSupport advised;

		//构造方法
		public DynamicAdvisedInterceptor(AdvisedSupport advised) {
			this.advised = advised;
		}

		@Override
		@Nullable
		public Object intercept(Object proxy, 
								Method method, 
								Object[] args, 
								MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			boolean setProxyContext = false;
			Object target = null;
			TargetSource targetSource = this.advised.getTargetSource();
			try {
				if (this.advised.exposeProxy) {
					// Make invocation available if necessary.
					oldProxy = AopContext.setCurrentProxy(proxy);
					setProxyContext = true;
				}

				//尽可能晚的持有目标对象，以缩短持有目标对象的时候，因为其可能来自于对象池
				// Get as late as possible to minimize the time we "own" the target, in case it comes from a pool...
				target = targetSource.getTarget();
				
				//目标对象的Class对象
				Class<?> targetClass = (target != null ? target.getClass() : null);
				/*			    
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
				List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);   // 见AdvisedSupport类代码10
				//当前方法的返回值
				Object retVal;

				//如果没有匹配的Advice，那就直接调用目标对象上的方法
				// Check whether we only have one InvokerInterceptor: that is,
				// no real advice, but just reflective invocation of the target.
				if (chain.isEmpty() && CglibMethodInvocation.isMethodProxyCompatible(method)) {
					// We can skip creating a MethodInvocation: just invoke the target directly.
					// Note that the final invoker must be an InvokerInterceptor, so we know
					// it does nothing but a reflective operation on the target, and no hot
					// swapping or fancy proxying.
					Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
					try {
						retVal = methodProxy.invoke(target, argsToUse);
					}
					catch (CodeGenerationException ex) {
						CglibMethodInvocation.logFastClassGenerationFailure(method);
						retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
					}
				}
				else {

					/*
					
					首先，创建Joinpoint对象,实现类为：CglibMethodInvocation(当前类内部类)，见Cglib2AopProxy.CglibMethodInvocation构造方法
					
						I:        org.aopalliance.intercept.Joinpoint
												|
						I:        org.aopalliance.intercept.Invocation
												|								          
						I:      org.aopalliance.intercept.MethodInvocation
												|								         
						I:     org.springframework.aop.ProxyMethodInvocation
												|								         
						C:    org.springframework.aop.framework.ReflectiveMethodInvocation        // 实现了proceed()方法
												|								         
						C:  org.springframework.aop.framework.Cglib2AopProxy.CglibMethodInvocation
					
					然后，执行Joinpoint对象的proceed()方法，该方法的定义：
							Proceeds to the next interceptor in the chain.   CglibAopProxy.CglibMethodInvocation
							
					proceed()方法定义在CglibMethodInvocation的父类ReflectiveMethodInvocation，见其代码1
					*/	
					// We need to create a method invocation...
					retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy).proceed();
				}
				retVal = processReturnType(proxy, target, method, retVal);
				return retVal;
			}
			finally {
				if (target != null && !targetSource.isStatic()) {
					targetSource.releaseTarget(target);
				}
				if (setProxyContext) {
					// Restore old proxy.
					AopContext.setCurrentProxy(oldProxy);
				}
			}
		}



		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other ||
					(other instanceof DynamicAdvisedInterceptor &&
							this.advised.equals(((DynamicAdvisedInterceptor) other).advised)));
		}

		/**
		 * CGLIB uses this to drive proxy creation.
		 */
		@Override
		public int hashCode() {
			return this.advised.hashCode();
		}
	}

 

}
