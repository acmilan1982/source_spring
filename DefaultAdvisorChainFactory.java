/*
 * Copyright 2002-2018 the original author or authors.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

/**
A simple but definitive way of working out an advice chain for a Method, given an Advised object. Always rebuilds each advice chain; caching can be provided by subclasses.
Since:
2.0.3
Author:
Juergen Hoeller, Rod Johnson, Adrian Colyer
 
        从AdvisedSupport提供的Advisor  chain(持有一个或多个Advisor)中，获取可作用于当前方法上的Advice(MethodInterceptor)链表
        通常，在2个时间点上会调用当前方法：
        1. 创建代理对象时，ProxyCallbackFilter会调用当前方法，用来判断目标对象上的方法是否有可用的Advisor

        2. 执行代理对象的方法时，会调用当前方法   
        

        1. 最终可用的advice,都会被转换成 org.aopalliance.intercept.MethodInterceptor 类型，以便于多个advice之间的递归执行
        2. 返回的advice链表，通常会被缓存(key为当前Method)在AdvisedSupport中(methodCache)
            如果代理对象的advice不被修改(增加或删除)，那么下次执行代理对象的当前方法时，可直接从缓存中获取可用的advice链表
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {


    /*
 
                            
            遍历AdvisedSupport提供的所有Advisor对象，根据其Pointcut与当前方法进行静态或动态匹配
            匹配成功的，把非org.aopalliance.intercept.MethodInterceptor类型的advice
            都转换成该类型的advice,以便统一用invoke方法调用源advice中的方法
            最终返回MethodInterceptor对象组成的List
            
            如果需要进行动态匹配，此处未进行匹配，而是把该MethodInterceptor封装成InterceptorAndDynamicMethodMatcher对象，加入List:
            new InterceptorAndDynamicMethodMatcher(interceptor, mm)
            
            最后返回可用于当前Method的所有MethodInterceptor(List)
    */	
	// 代码1
	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, 
																	Method method, 
																	@Nullable Class<?> targetClass) {

        //获取AdvisorAdapterRegistry对象，实现类为DefaultAdvisorAdapterRegistry        
		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
		
        // getAdvisors()方法获取 AdvisedSupport 里的所有Advisor：private List<Advisor> advisors = new ArrayList<>();
		Advisor[] advisors = config.getAdvisors();

        // 这个list就是当前方法返回值，为了避免在添加advice对象中进行数组扩容的操作
        // 所以一开始数组就初始化为可能的最大值
		List<Object> interceptorList = new ArrayList<>(advisors.length);
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		Boolean hasIntroductions = null;


        /*
            
            AdvisedSupport是Advised接口的实现类，所以当前目标对象所配置的Advisor实例都保存在AdvisedSupport对象中
        
            遍历当前目标对象配置的所有Advisor
        */	
		for (Advisor advisor : advisors) {
			if (advisor instanceof PointcutAdvisor) {
				// Add it conditionally.
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
                /*
                    1.对于编程式的AOP，AdvisedSupport的preFiltered为flase
                    那么从Advisor中获取Pointcut，首先进行类的匹配
                    pointcutAdvisor.getPointcut().getClassFilter()：获取Pointcut的ClassFilter对象，执行其
                    boolean matches(Class<?> clazz) 方法，进行类的匹配
                */		
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
                    // 从Pointcut中获取MethodMatcher，以进行方法的匹配
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					boolean match;

					if (mm instanceof IntroductionAwareMethodMatcher) {
						if (hasIntroductions == null) {
							hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
						}
						match = ((IntroductionAwareMethodMatcher) mm).matches(method, actualClass, hasIntroductions);
					}
					else {

                        /*
							首先进行静态匹配，即不考虑方法执行时传入的参数：
							执行MethodMatcher：boolean matches(Method method, Class<?> targetClass)										  									   												   
                        */	                       
						match = mm.matches(method, actualClass);
					}


					if (match) {
                        /*						
                            从Advisor中提取Advice，通常一个Advisor中有一个Advice
                                所有非org.aopalliance.intercept.MethodInterceptor类型的advice
                                都转换成该类型的advice,以便统一用invoke方法调用源advice中的方法
                                具体处理见DefaultAdvisorAdapterRegistry类代码2
                        */	
						MethodInterceptor[] interceptors = registry.getInterceptors(advisor);  // registry: 当前方法的局部变量：AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
						                                                                       // getInterceptors 方法见 DefaultAdvisorAdapterRegistry 代码3
                        /*
                                如果静态匹配成功，那么由MethodMatcher的isRuntime()决定是否进行动态匹配(即考虑方法执行时传入的参数)：
                                执行MethodMatcher：boolean matches(Method method, Class<?> targetClass, Object[] args)														
                                
                                如果需要进行动态匹配，照理应该执行上述matches方法，那么根据方法参数，当前Interceptor可能会应用可能不会应用
                                但是出于效率考虑需要缓存应用于当前方法的所有Interceptor，因此在InterceptorChain中保留该Interceptor，将是否应用延时决定，具体处理：
                                    把该MethodInterceptor封装成InterceptorAndDynamicMethodMatcher对象，加入List:
                                    InterceptorAndDynamicMethodMatcher持有MethodMatcher：先执行3个参数matches方法以决定是否应用该Interceptor
                                                                                                                            
                        */	
						if (mm.isRuntime()) {
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
                            /*
                                只进行静态匹配方法，把经过类型转换，已经是org.aopalliance.intercept.MethodInterceptor类型的advice
                                加入interceptorList，最终返回该interceptorList													   
                            */	
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			else if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			else {
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}

		return interceptorList;
	}

	/**
	 * Determine whether the Advisors contain matching introductions.
	 */
	private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (ia.getClassFilter().matches(actualClass)) {
					return true;
				}
			}
		}
		return false;
	}

}
