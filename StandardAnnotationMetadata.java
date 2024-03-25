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

package org.springframework.core.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ReflectionUtils;

/**
	AnnotationMetadata implementation that uses standard reflection to introspect a given Class.

    AnnotationMetadata

	Since:
	  2.5
	Author:
	  Juergen Hoeller, Mark Fisher, Chris Beams, Phillip Webb, Sam Brannen

	AnnotationMetadata的实现，使用反射读取类的注解  
 */
public class StandardAnnotationMetadata extends StandardClassMetadata implements AnnotationMetadata {

	private final MergedAnnotations mergedAnnotations;

	private final boolean nestedAnnotationsAsMap;

	@Nullable
	private Set<String> annotationTypes;

 
	/**
        Create a new StandardAnnotationMetadata wrapper for the given Class, providing the option to return any nested annotations or annotation arrays 
		in the form of org.springframework.core.annotation.AnnotationAttributes instead of actual Annotation instances.
        Deprecated
        since 5.2 in favor of the factory method AnnotationMetadata.introspect(Class). Use MergedAnnotation.asMap from getAnnotations() rather than getAnnotationAttributes(String) if nestedAnnotationsAsMap is false
        Params:
         introspectedClass – the Class to introspect 
		 nestedAnnotationsAsMap – return nested annotations and annotation arrays as org.springframework.core.annotation.AnnotationAttributes for compatibility with ASM-based AnnotationMetadata implementations
        Since:

		读取注解，以 org.springframework.core.annotation.AnnotationAttributes 的方式替代 Annotation
	 */
	// 构造方法
	@Deprecated
	public StandardAnnotationMetadata(Class<?> introspectedClass, 
                                      boolean nestedAnnotationsAsMap) {  // true
		super(introspectedClass);  // 父类 StandardClassMetadata 持有 introspectedClass

 
		/*
		 *    创建MergedAnnotations对象，实现类为：TypeMappedAnnotations
				1. 判断当前对象是否为jdk的对象，
					或者是否没有任何注解，
					处理过程中，会	获取当前对象声明的所有注解，并缓存至AnnotationsScanner
								   获取每个注解的的所有属性方法，并缓存至AttributeMethods
				2. 创建当前Class对象对应的 MergedAnnotations对象，实现类为：TypeMappedAnnotations
		 */
		this.mergedAnnotations = MergedAnnotations.from(introspectedClass,                             // 见MergedAnnotations代码10
                                                        SearchStrategy.INHERITED_ANNOTATIONS,          // SearchStrategy.INHERITED_ANNOTATIONS,
                                                        RepeatableContainers.none());                  // NoRepeatableContainers.INSTANCE;

		this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
	}

 
	/**
			Factory method to create a new AnnotationMetadata instance for the given class using standard reflection.
			Params:
			type – the class to introspect
			Returns:
			a new AnnotationMetadata instance
			Since:
			5.2

            为指定的Class创建AnnotationMetadata对象，使用反射读取注解
	 */
	// 代码10
	static AnnotationMetadata from(Class<?> introspectedClass) {
		return new StandardAnnotationMetadata(introspectedClass, 
		               true);  // 见StandardAnnotationMetadata构造方法
	}


	/**
     
	 */
	// 代码15
	@Override
	@Nullable
	public Map<String, Object> getAnnotationAttributes(String annotationName,           // 注解的全限定类名，如：org.springframework.context.annotation.Configuration
	                                                   boolean classValuesAsString) {   // false
		if (this.nestedAnnotationsAsMap) {  // true
			// AnnotationMetadata.super: AnnotatedTypeMetadata
			return AnnotationMetadata.super.getAnnotationAttributes(annotationName, classValuesAsString);  // getAnnotationAttributes见该类代码3
		}
		return AnnotatedElementUtils.getMergedAnnotationAttributes(
				getIntrospectedClass(), annotationName, classValuesAsString, false);
	}	


	/**
	 *  
        Apply processing and build a complete ConfigurationClass by reading the annotations, members and methods from the source class. This method can be called multiple times as relevant sources are discovered.
        Params:
          configClass – the configuration class being build sourceClass – a source class
        Returns:
          the superclass, or null if none found or previously processed
     * 
	 */
	// 代码16
	@Override
	public MergedAnnotations getAnnotations() {
		return this.mergedAnnotations;
	}


 

	/**
	 *  
		Description copied from interface: AnnotationMetadata
		Retrieve the method metadata for all methods that are annotated (or meta-annotated) with the given annotation type.
		For any returned method, AnnotatedTypeMetadata.isAnnotated(java.lang.String) will return true for the given annotation type.

		Specified by:
		   getAnnotatedMethods in interface AnnotationMetadata
		Parameters:
		   annotationName - the fully qualified class name of the annotation type to look for
		Returns:
		   a set of MethodMetadata for methods that have a matching annotation. The return value will be an empty set if no methods match the annotation type.

		获取被 指定注解 注解的方法元数据   
     * 
	 */
	// 代码20
	@Override
	@SuppressWarnings("deprecation")
	public Set<MethodMetadata> getAnnotatedMethods(String annotationName) {
		Set<MethodMetadata> annotatedMethods = null;
		if (AnnotationUtils.isCandidateClass(getIntrospectedClass(), annotationName)) {
			try {
				// 获取当前类的所有方法
				Method[] methods = ReflectionUtils.getDeclaredMethods(getIntrospectedClass());  // 见ReflectionUtils代码10
				for (Method method : methods) {
					if (isAnnotatedMethod(method, annotationName)) {
						if (annotatedMethods == null) {
							annotatedMethods = new LinkedHashSet<>(4);
						}
						annotatedMethods.add(new StandardMethodMetadata(method, this.nestedAnnotationsAsMap));
					}
				}
			}
			catch (Throwable ex) {
				throw new IllegalStateException("Failed to introspect annotated methods on " + getIntrospectedClass(), ex);
			}
		}
		return (annotatedMethods != null ? annotatedMethods : Collections.emptySet());
	}


	/**
	 *  
 
     * 
	 */
	// 代码21
	private static boolean isAnnotatedMethod(Method method, String annotationName) {
		return !method.isBridge() && method.getAnnotations().length > 0 &&
				AnnotatedElementUtils.isAnnotated(method, annotationName);
	}


}
