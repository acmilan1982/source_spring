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

package org.springframework.aop.framework.adapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;

/**
    Default implementation of the AdvisorAdapterRegistry interface. Supports MethodInterceptor, org.springframework.aop.MethodBeforeAdvice, org.springframework.aop.AfterReturningAdvice, org.springframework.aop.ThrowsAdvice.
    Author:
    Rod Johnson, Rob Harrop, Juergen Hoeller

    将一个类的接口转换成客户希望的另外一个接口。
    Adapter模式使得原本由于接口不兼容而不能一起工作的那些类可以一起工作。(GoF)	
    
    当前类提供了类似适配器模式的操作，把除MethodInterceptor类型之外的advice，
    都转换成MethodInterceptor接口类型的实例，统一用invoke方法调用源advice中各自对应的方法   
 */
@SuppressWarnings("serial")
public class DefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry, Serializable {

	private final List<AdvisorAdapter> adapters = new ArrayList<>(3);


    /**
		Create a new DefaultAdvisorAdapterRegistry, registering well-known adapters.
    */
    //
	public DefaultAdvisorAdapterRegistry() {
		registerAdvisorAdapter(new MethodBeforeAdviceAdapter());
		registerAdvisorAdapter(new AfterReturningAdviceAdapter());
		registerAdvisorAdapter(new ThrowsAdviceAdapter());
	}

	@Override
	public void registerAdvisorAdapter(AdvisorAdapter adapter) {
		this.adapters.add(adapter);
	}


    /*
 
		Description copied from interface: AdvisorAdapterRegistry

		Return an array of AOP Alliance MethodInterceptors to allow use of the given Advisor in an interception-based framework.
		Don't worry about the pointcut associated with the Advisor, if it is a PointcutAdvisor: just return an interceptor.

		Specified by:
		getInterceptors in interface AdvisorAdapterRegistry
		Parameters:
		advisor - the Advisor to find an interceptor for
		Returns:
		an array of MethodInterceptors to expose this Advisor's behavior
		Throws:
		UnknownAdviceTypeException - if the Advisor type is not understood by any registered AdvisorAdapter    
		
		返回值类型： org.aopalliance.intercept.MethodInterceptor

    */	
	// 代码3
	@Override
	public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
		List<MethodInterceptor> interceptors = new ArrayList<>(3);
        // 从Advisor中提取Advice
		Advice advice = advisor.getAdvice();



        //除MethodInterceptor类型之外的advice，
        //都转换成MethodInterceptor接口类型的实例，统一用invoke方法调用源advice中各自对应的方法
        /*
            所有非org.aopalliance.intercept.MethodInterceptor类型的advice
            都转换成该类型的advice,以便统一用invoke方法调用源advice中的方法
            
            每种非MethodInterceptor类型的advice，都提供了对应的AdvisorAdapter对象来进行处理：
            MethodBeforeAdviceAdapter：
                public boolean supportsAdvice(Advice advice) {
                        return (advice instanceof MethodBeforeAdvice);
                    }							  
                
                ThrowsAdviceAdapter：
                    public boolean supportsAdvice(Advice advice) {
                        return (advice instanceof ThrowsAdvice);
                    }								
                    
              AfterReturningAdviceAdapter：
                    public boolean supportsAdvice(Advice advice) {
                        return (advice instanceof AfterReturningAdvice);
                    }     
                    
            找到对应的AdvisorAdapter后，该AdvisorAdapter把advice包装成org.aopalliance.intercept.MethodInterceptor对象           
            MethodBeforeAdviceAdapter：
                        public MethodInterceptor getInterceptor(Advisor advisor) {
                            MethodBeforeAdvice advice = (MethodBeforeAdvice) advisor.getAdvice();
                            //public class MethodBeforeAdviceInterceptor implements MethodInterceptor
                            return new MethodBeforeAdviceInterceptor(advice);
                        }						  
                                                                
                ThrowsAdviceAdapter：
                        public MethodInterceptor getInterceptor(Advisor advisor) {
                            //public class ThrowsAdviceInterceptor implements MethodInterceptor, AfterAdvice
                            return new ThrowsAdviceInterceptor(advisor.getAdvice());
                        }									
                
AfterReturningAdviceAdapter：
                        public MethodInterceptor getInterceptor(Advisor advisor) {												
                            AfterReturningAdvice advice = (AfterReturningAdvice) advisor.getAdvice();
                            //public class AfterReturningAdviceInterceptor implements MethodInterceptor, AfterAdvice
                            return new AfterReturningAdviceInterceptor(advice);
                        }		  								 										 
        */						    					        
		if (advice instanceof MethodInterceptor) {
			interceptors.add((MethodInterceptor) advice);
		}

        
		for (AdvisorAdapter adapter : this.adapters) {
			if (adapter.supportsAdvice(advice)) {
				interceptors.add(adapter.getInterceptor(advisor));
			}
		}
		
		if (interceptors.isEmpty()) {
			throw new UnknownAdviceTypeException(advisor.getAdvice());
		}
		return interceptors.toArray(new MethodInterceptor[0]);
	}



    
	@Override
	public Advisor wrap(Object adviceObject) throws UnknownAdviceTypeException {
		if (adviceObject instanceof Advisor) {
			return (Advisor) adviceObject;
		}
		if (!(adviceObject instanceof Advice)) {
			throw new UnknownAdviceTypeException(adviceObject);
		}
		Advice advice = (Advice) adviceObject;
		if (advice instanceof MethodInterceptor) {
			// So well-known it doesn't even need an adapter.
			return new DefaultPointcutAdvisor(advice);
		}
		for (AdvisorAdapter adapter : this.adapters) {
			// Check that it is supported.
			if (adapter.supportsAdvice(advice)) {
				return new DefaultPointcutAdvisor(advice);
			}
		}
		throw new UnknownAdviceTypeException(advice);
	}


}
