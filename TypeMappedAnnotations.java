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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.lang.Nullable;

/**
    MergedAnnotations implementation that searches for and adapts annotations and meta-annotations using AnnotationTypeMappings.
 *
 * @author Phillip Webb
 * @since 5.2
 */
final class TypeMappedAnnotations implements MergedAnnotations {

	/**
	 * Shared instance that can be used when there are no annotations.
	 */
	static final MergedAnnotations NONE = new TypeMappedAnnotations(
			null, new Annotation[0], RepeatableContainers.none(), AnnotationFilter.ALL);


	// 待搜索注解所在的AnnotatedElement对象，类或者方法
	@Nullable
	private final Object source;

	// 待搜索注解所在的AnnotatedElement对象，类或者方法
	@Nullable
	private final AnnotatedElement element;

	@Nullable
	private final SearchStrategy searchStrategy;

	@Nullable
	private final Annotation[] annotations;

	// NoRepeatableContainers
	private final RepeatableContainers repeatableContainers;

	// 构造方法中被初始化
	// AnnotationFilter PLAIN = packages("java.lang", "org.springframework.lang");
	private final AnnotationFilter annotationFilter;

	@Nullable
	private volatile List<Aggregate> aggregates;

	/**
 

	 */
	// 构造方法1
	private TypeMappedAnnotations(AnnotatedElement element,                    // 待搜索注解所在的AnnotatedElement对象，类或者方法
								  SearchStrategy searchStrategy,               // SearchStrategy.INHERITED_ANNOTATIONS / SearchStrategy.TYPE_HIERARCHY
								  RepeatableContainers repeatableContainers,   // NoRepeatableContainers
								  AnnotationFilter annotationFilter) {         // AnnotationFilter.PLAIN  packages("java.lang", "org.springframework.lang");

		this.source = element;
		this.element = element;
		this.searchStrategy = searchStrategy;
		this.annotations = null;
		this.repeatableContainers = repeatableContainers;
		this.annotationFilter = annotationFilter;
	}
 

	/**
          1. 判断当前对象是否为jdk的对象，
			 或者是否没有任何注解，
			 处理过程中，会	获取当前对象声明的所有注解，并缓存至AnnotationsScanner
						   获取每个注解的的所有属性方法，并缓存至AttributeMethods
		  2. 创建当前Class对象对应的 MergedAnnotations对象，实现类为：TypeMappedAnnotations
	 */
	// 代码5
	static MergedAnnotations from(AnnotatedElement element, 
								  SearchStrategy searchStrategy,              // SearchStrategy.INHERITED_ANNOTATIONS,
								  RepeatableContainers repeatableContainers,  // NoRepeatableContainers
								  AnnotationFilter annotationFilter) {        // AnnotationFilter.PLAIN

 
		/**
			 判断当前对象是否为jdk的对象，
			 或者是否没有任何注解，
			 处理过程中，会	获取当前对象声明的所有注解，并缓存至AnnotationsScanner
						   获取每个注解的的所有属性方法，并缓存至AttributeMethods
		*/					
		if (AnnotationsScanner.isKnownEmpty(element, searchStrategy)) {       // 见AnnotationsScanner代码5
			return NONE;
		}
		return new TypeMappedAnnotations(element, searchStrategy, repeatableContainers, annotationFilter);
	}


	/**

	 */
	// 代码7
	@Override
	public <A extends Annotation> MergedAnnotation<A> get(Class<A> annotationType) {  // 待搜索的注解
		return get(annotationType, null, null);   // 见代码8
	}



	/**
         获取当前AnnotatedElement对象(类或者方法)上指定注解的属性
	 */
	// 代码8
 	@Override
	public <A extends Annotation> MergedAnnotation<A> get(String annotationType,                                       // 待搜索的注解类型，比如：@Service/注解的全限定类名，如：org.springframework.context.annotation.Configuration
														  @Nullable Predicate<? super MergedAnnotation<A>> predicate,  // null
														  @Nullable MergedAnnotationSelector<A> selector) {            // null/FirstDirectlyDeclared
  
		if (this.annotationFilter.matches(annotationType)) {  // AnnotationFilter PLAIN = packages("java.lang", "org.springframework.lang");
			return MergedAnnotation.missing();
		}

		MergedAnnotation<A> result = scan(annotationType,                               // scan方法见代码10
				                          new MergedAnnotationFinder<>(annotationType,  // 注解的全限定类名，如：org.springframework.context.annotation.Configuration
																	   predicate,       // null
																	   selector));      // null/new FirstDirectlyDeclared()
		return (result != null ? result : MergedAnnotation.missing());
	}

 




	/**

	 */
	// 代码10
	@Nullable
	private <C, R> R scan(C criteria,                                // 待搜索的注解，注解的全限定类名，如：org.springframework.context.annotation.Configuration，org.springframework.context.annotation.ComponentScan等
	                      AnnotationsProcessor<C, R> processor) {    // MergedAnnotationFinder，当前类的内部类
		if (this.annotations != null) {
			R result = processor.doWithAnnotations(criteria, 0, this.source, this.annotations);
			return processor.finish(result);
		}
		
		if (this.element != null && this.searchStrategy != null) {
			
			// 见AnnotationsScanner代码15    
			return AnnotationsScanner.scan(criteria,                   // 待搜索的注解类型                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             scan(criteria,                   // 待搜索的注解，注解的全限定类名，如：org.springframework.context.annotation.Configuration，org.springframework.context.annotation.ComponentScan等
										   this.element,               // 待搜索注解所在的AnnotatedElement对象
										   this.searchStrategy, 
										   processor);
		}
		return null;
	}




	/**

	 */
	// 代码11
	@Override
	public boolean isPresent(String annotationType) {
		if (this.annotationFilter.matches(annotationType)) {
			return false;
		}
		return Boolean.TRUE.equals(scan(annotationType,
				IsPresent.get(this.repeatableContainers, this.annotationFilter, false)));
	}


 






	/**

	 */
	// 代码15
	private static boolean isMappingForType(AnnotationTypeMapping mapping,
			                                AnnotationFilter annotationFilter, 
											@Nullable Object requiredType) {       // 待搜索的注解

		Class<? extends Annotation> actualType = mapping.getAnnotationType();
		return (!annotationFilter.matches(actualType) &&
				(requiredType == null || actualType == requiredType || actualType.getName().equals(requiredType)));
	}





 






}
