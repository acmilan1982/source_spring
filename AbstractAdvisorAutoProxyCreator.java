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

package org.springframework.aop.framework.autoproxy;

import java.util.List;

import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Generic auto proxy creator that builds AOP proxies for specific beans
 * based on detected Advisors for each bean.
 *
 * <p>Subclasses may override the {@link #findCandidateAdvisors()} method to
 * return a custom list of Advisors applying to any object. Subclasses can
 * also override the inherited {@link #shouldSkip} method to exclude certain
 * objects from auto-proxying.
 *
 * <p>Advisors or advices requiring ordering should be annotated with
 * {@link org.springframework.core.annotation.Order @Order} or implement the
 * {@link org.springframework.core.Ordered} interface. This class sorts
 * advisors using the {@link AnnotationAwareOrderComparator}. Advisors that are
 * not annotated with {@code @Order} or don't implement the {@code Ordered}
 * interface will be considered as unordered; they will appear at the end of the
 * advisor chain in an undefined order.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #findCandidateAdvisors
 */
@SuppressWarnings("serial")
public abstract class AbstractAdvisorAutoProxyCreator extends AbstractAutoProxyCreator {

	@Nullable
	private BeanFactoryAdvisorRetrievalHelper advisorRetrievalHelper;


	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		super.setBeanFactory(beanFactory);
		if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
			throw new IllegalArgumentException(
					"AdvisorAutoProxyCreator requires a ConfigurableListableBeanFactory: " + beanFactory);
		}
		initBeanFactory((ConfigurableListableBeanFactory) beanFactory);
	}

	protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		this.advisorRetrievalHelper = new BeanFactoryAdvisorRetrievalHelperAdapter(beanFactory);
	}

    /**

		当前方法从容器中获取可用于当前bean的Advisor，由于可能有多个Advisor，所以使用数组来保存这些Advisor：
		
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
    // 代码5
	@Override
	@Nullable
	protected Object[] getAdvicesAndAdvisorsForBean(
			Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {
		/*
			获取可用于当前目标类的Advisor
			
		*/	
		List<Advisor> advisors = findEligibleAdvisors(beanClass, beanName);           // 见代码6
		/*
			如果没有适用于当前目标对象的Advisor，返回DO_NOT_PROXY，表示不创建代理对象   
		*/	
		if (advisors.isEmpty()) {
			return DO_NOT_PROXY;
		}
		return advisors.toArray();
	}

    /**
        Find all eligible Advisors for auto-proxying this class.

        Params:
          beanClass – the clazz to find advisors for 
          beanName – the name of the currently proxied bean
        Returns:
          the empty List, not null, if there are no pointcuts or interceptors
        See Also:
          findCandidateAdvisors, sortAdvisors, extendAdvisors

		获取适用于当前类的所有  Advisor
    */
    // 代码6
	protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, 
                                                 String beanName) {
		/*
			findCandidateAdvisors()方法获取候选Advisor：			
			候选Advisor通常有两个来源:     
			1. 在配置文件中注册Advisor
				另外，还有隐式注册的Advisor，配置文件中的某些标签，系统在解析时，会注册相关的Advisor
				如基于Annotation的事务管理(<tx:annotation-driven>)，系统会注册BeanFactoryTransactionAttributeSourceAdvisor
				具体处理见AnnotationDrivenBeanDefinitionParser 
				
			2. 提供@Aspect标注的类，spring aop会识别并拼装成Advisor
			
			
			findCandidateAdvisors()的默认实现(当前类中)可以获取通过配置注册的Advisor,见当前类代码3
			子类可以重写该方法，使用自定义的获取方法
			
			如：DefaultAdvisorAutoProxyCreator只使用配置文件中注册的Advisor，因此未重写findCandidateAdvisors()
				而AnnotationAwareAspectJAutoProxyCreator重写了findCandidateAdvisors()
					step1:首先会调用当前类的findCandidateAdvisors()获取通过配置注册的Advisor
					step2:然后再获取@Aspect标注的Advisor
					见AnnotationAwareAspectJAutoProxyCreator类代码1   	 
		
		*/									
		List<Advisor> candidateAdvisors = findCandidateAdvisors();
						/*
							  获取候选Advisor之后，从其中找出可作用于当前bean的Advisor：
			            使用Advisor中的Pointcut进行初步匹配，即类和静态的方法匹配，排除不可匹配的Advisor
			            但由于尚未能获取方法的参数，所以无法进行动态的方法匹配，直到方法被调用时，才能进行动态的方法匹配
			            因此，只要类和静态的方法能匹配，该Advisor即为可用Advisor
			            另外，动态MethodMatcher的boolean matches(Method method, Class<?> targetClass)通常为true
			           
							  见代码4
						*/	
		List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
						/*
								Extension hook that subclasses can override to register additional Advisors, given the sorted Advisors obtained to date. 								
								The default implementation is empty. 							
								Typically used to add Advisors that expose contextual information required by some of the later advisors.
               
							  在获取当前bean可用Advisor chain之后，extendAdvisors方法可以让自动代理机制以编程的方式在可用Advisor list之中加入额外的Advisor
							  该方法默认实现为空方法，DefaultAdvisorAutoProxyCreator未重写该方法
							  
							  
							  通常重写该方法注册额外的Advisor，提供后面Advisor所需的上下文信息，比如:							  
								  AspectJAwareAdvisorAutoProxyCreator重新了该方法：							  
									  如果Advisor chain使用了AsepctJ风格的Advisor，那么在Advisor chain的链表头，加入ExposeInvocationInterceptor.ADVISOR
									  该Advisor持有的Advice第一个被执行:把MethodInvocation对象绑定至当前线程
									  
									  因为AsepctJ风格的advice其参数可能是org.aspectj.lang.JoinPoint对象，
									  而Spring提供的org.aspectj.lang.JoinPoint对象，其中封装了MethodInvocation对象
									  org.aspectj.lang.JoinPoint对象上所有的方法调用，实际委托给MethodInvocation对象执行	
									见AspectJAwareAdvisorAutoProxyCreator类代码1  
								  						  
						*/	
		extendAdvisors(eligibleAdvisors);
						/*
							  对可用Advisor chain进行排序  
						*/	
		if (!eligibleAdvisors.isEmpty()) {
			eligibleAdvisors = sortAdvisors(eligibleAdvisors);
		}
		return eligibleAdvisors;
	}

    /**
 默认实现，获取通过配置注册的Advisor 

		findCandidateAdvisors()方法获取候选Advisor：			
			候选Advisor通常有两个来源:     
				1. 在配置文件中注册Advisor
					另外，还有隐式注册的Advisor，配置文件中的某些标签，系统在解析时，会注册相关的Advisor
					如基于Annotation的事务管理(<tx:annotation-driven>)，系统会注册BeanFactoryTransactionAttributeSourceAdvisor
					具体处理见AnnotationDrivenBeanDefinitionParser 
					
				2. 提供@Aspect标注的类，spring aop会识别并拼装成Advisor
		
		
		当前方法为默认实现，可以获取通过配置注册的Advisor
		子类可以重写该方法，使用自定义的获取方法
		
		如：DefaultAdvisorAutoProxyCreator只使用配置文件中注册的Advisor，因此未重写findCandidateAdvisors()
			而AnnotationAwareAspectJAutoProxyCreator重写了findCandidateAdvisors()
				step1:首先会调用当前类的findCandidateAdvisors()获取通过配置注册的Advisor
				step2:然后再获取@Aspect标注的Advisor
				见AnnotationAwareAspectJAutoProxyCreator类代码1   
    */
    // 代码6
	protected List<Advisor> findCandidateAdvisors() {
		Assert.state(this.advisorRetrievalHelper != null, "No BeanFactoryAdvisorRetrievalHelper available");
		/*
			advisorRetrievalHelper为当前类属性，在当前类实例化过程中已经被初始化：
			this.advisorRetrievalHelper = new BeanFactoryAdvisorRetrievalHelperAdapter(beanFactory);
			
			该属性为BeanFactoryAdvisorRetrievalHelper对象，用来从BeanFactory中获取标准的Advisor：
			Helper for retrieving standard Spring Advisors from a BeanFactory, for use with auto-proxying.

				BeanFactoryAdvisorRetrievalHelper
								|
				BeanFactoryAdvisorRetrievalHelperAdapter
				
			BeanFactoryAdvisorRetrievalHelperAdapter为当前类的内部类
			findAdvisorBeans()方法见其父类代码1
		*/	
		return this.advisorRetrievalHelper.findAdvisorBeans();   // 见BeanFactoryAdvisorRetrievalHelper代码1
	}


					/**
								Search the given candidate Advisors to find all Advisors that can apply to the specified bean.
								
								Parameters:
									candidateAdvisors  the candidate Advisors
									beanClass          the target's bean class
									beanName           the target's bean name
								Returns:
								  the List of applicable Advisors
							  
							  获取候选Advisor之后，从其中找出可作用于当前bean的Advisor：
			            使用Advisor中的Pointcut进行初步匹配，即类和静态的方法匹配，排除不可匹配的Advisor
			            但由于尚未能获取方法的参数，所以无法进行动态的方法匹配，直到方法被调用时，才能进行动态的方法匹配
			            因此，只要类和静态的方法能匹配，该Advisor即为可用Advisor
			            另外，动态MethodMatcher的boolean matches(Method method, Class<?> targetClass)通常为true					  
					 */
	protected List<Advisor> findAdvisorsThatCanApply(
			List<Advisor> candidateAdvisors, Class<?> beanClass, String beanName) {

		ProxyCreationContext.setCurrentProxiedBeanName(beanName);
		try {
			return AopUtils.findAdvisorsThatCanApply(candidateAdvisors, beanClass);
		}
		finally {
			ProxyCreationContext.setCurrentProxiedBeanName(null);
		}
	}

	/**
	 * Return whether the Advisor bean with the given name is eligible
	 * for proxying in the first place.
	 * @param beanName the name of the Advisor bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleAdvisorBean(String beanName) {
		return true;
	}

	/**
	 * Sort advisors based on ordering. Subclasses may choose to override this
	 * method to customize the sorting strategy.
	 * @param advisors the source List of Advisors
	 * @return the sorted List of Advisors
	 * @see org.springframework.core.Ordered
	 * @see org.springframework.core.annotation.Order
	 * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
	 */
	protected List<Advisor> sortAdvisors(List<Advisor> advisors) {
		AnnotationAwareOrderComparator.sort(advisors);
		return advisors;
	}

	/**
	 * Extension hook that subclasses can override to register additional Advisors,
	 * given the sorted Advisors obtained to date.
	 * <p>The default implementation is empty.
	 * <p>Typically used to add Advisors that expose contextual information
	 * required by some of the later advisors.
	 * @param candidateAdvisors the Advisors that have already been identified as
	 * applying to a given bean
	 */
	protected void extendAdvisors(List<Advisor> candidateAdvisors) {
	}

	/**
	 * This auto-proxy creator always returns pre-filtered Advisors.
	 */
	@Override
	protected boolean advisorsPreFiltered() {
		return true;
	}


	/**
	 * Subclass of BeanFactoryAdvisorRetrievalHelper that delegates to
	 * surrounding AbstractAdvisorAutoProxyCreator facilities.
	 */
	private class BeanFactoryAdvisorRetrievalHelperAdapter extends BeanFactoryAdvisorRetrievalHelper {

		public BeanFactoryAdvisorRetrievalHelperAdapter(ConfigurableListableBeanFactory beanFactory) {
			super(beanFactory);
		}

		@Override
		protected boolean isEligibleBean(String beanName) {
			return AbstractAdvisorAutoProxyCreator.this.isEligibleAdvisorBean(beanName);
		}
	}

}
