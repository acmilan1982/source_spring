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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**

	Provides access to a collection of merged annotations, usually obtained from a source such as a Class or Method.
	提供对合并注解集合的访问，这些注解通常是从类或方法等源获取的。

	Each merged annotation represents a view where the attribute values may be "merged" from different source values, typically:
	● Explicit and Implicit @AliasFor declarations on one or more attributes within the annotation
	● Explicit @AliasFor declarations for a meta-annotation
	● Convention based attribute aliases for a meta-annotation
	● From a meta-annotation declaration

	每个合并的注解都代表一个视图，其中的属性值可以从不同的源值“合并”而来，通常为：
	
	For example, a @PostMapping annotation might be defined as follows:
	@Retention(RetentionPolicy.RUNTIME)
	@RequestMapping(method = RequestMethod.POST)
	public @interface PostMapping {
	
		@AliasFor(attribute = "path")
		String[] value() default {};
	
		@AliasFor(attribute = "value")
		String[] path() default {};
	}
	
	If a method is annotated with @PostMapping("/home") it will contain merged annotations for both @PostMapping and the meta-annotation @RequestMapping. 
	The merged view of the @RequestMapping annotation will contain the following attributes:

	Name          Value              Source
	value        "/home"      Declared in @PostMapping
	path         "/home"      Explicit @AliasFor

	method RequestMethod.POST Declared in meta-annotation

	
	MergedAnnotations can be obtained from any Java AnnotatedElement. They may also be used for sources that don't use reflection (such as those that directly parse bytecode).
	Different search strategies can be used to locate related source elements that contain the annotations to be aggregated. For example, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY will search both superclasses and implemented interfaces.
	From a MergedAnnotations instance you can either get a single annotation, or stream all annotations or just those that match a specific type. You can also quickly tell if an annotation is present.
	
	Here are some typical examples:
	// is an annotation present or meta-present?
	mergedAnnotations.isPresent(ExampleAnnotation.class);
	
	// get the merged "value" attribute of ExampleAnnotation (either directly or
	// meta-present)
	mergedAnnotations.get(ExampleAnnotation.class).getString("value");
	
	// get all meta-annotations but no directly present annotations
	mergedAnnotations.stream().filter(MergedAnnotation::isMetaPresent);
	
	// get all ExampleAnnotation declarations (including any meta-annotations) and
	// print the merged "value" attributes
	mergedAnnotations.stream(ExampleAnnotation.class)
		.map(mergedAnnotation -> mergedAnnotation.getString("value"))
		.forEach(System.out::println);
	
	NOTE: The MergedAnnotations API and its underlying model have been designed for composable annotations in Spring's common component model, with a focus on attribute aliasing and meta-annotation relationships. There is no support for retrieving plain Java annotations with this API; please use standard Java reflection or Spring's AnnotationUtils for simple annotation retrieval purposes.
	Since:
	5.2
	See Also:
	MergedAnnotation, MergedAnnotationCollectors, MergedAnnotationPredicates, MergedAnnotationSelectors
	Author:
	Phillip Webb, Sam Brannen

 */
public interface MergedAnnotations extends Iterable<MergedAnnotation<Annotation>> {

 

	/**
		Create a new MergedAnnotations instance containing all annotations and meta-annotations from the specified element.
		The resulting instance will not include any inherited annotations. If you want to include those as well you should use from(AnnotatedElement, MergedAnnotations.SearchStrategy) with an appropriate MergedAnnotations.SearchStrategy.
		Params:
		  element – the source element
		Returns:
		  a MergedAnnotations instance containing the element's annotations

		创建MergedAnnotations，其包含 参数指定元素的 所有注解及元注解
	 */
	// 代码3
	static MergedAnnotations from(AnnotatedElement element) {
		return from(element, SearchStrategy.DIRECT);
	}
 

	/**
		Create a new MergedAnnotations instance containing all annotations and meta-annotations from the specified element and, depending on the MergedAnnotations.SearchStrategy, related inherited elements.
		Params:
		element – the source element searchStrategy – the search strategy to use
		Returns:
		a MergedAnnotations instance containing the merged element annotations
	 */
	// 代码4
	static MergedAnnotations from(AnnotatedElement element, SearchStrategy searchStrategy) {
		return from(element, searchStrategy, RepeatableContainers.standardRepeatables());
	}


	/**
		Create a new MergedAnnotations instance containing all annotations and meta-annotations from the specified element and, 
		depending on the MergedAnnotations.SearchStrategy, related inherited elements.
		Params:
		element – the source element searchStrategy – the search strategy to use repeatableContainers – the repeatable containers that may be used by the element annotations or the meta-annotations
		Returns:
		a MergedAnnotations instance containing the merged element annotations
	 */
	// 代码5
	static MergedAnnotations from(AnnotatedElement element,                    // 待搜索注解所在的AnnotatedElement对象
	                              SearchStrategy searchStrategy,               // SearchStrategy.TYPE_HIERARCHY
			                      RepeatableContainers repeatableContainers) {

		return from(element, searchStrategy, repeatableContainers, AnnotationFilter.PLAIN);         // 见代码
	}



 

	/**
        Create a new MergedAnnotations instance containing all annotations and meta-annotations from the specified element and, depending on the MergedAnnotations.SearchStrategy, related inherited elements.
        Params:
			element – the source element 
			searchStrategy – the search strategy to use 
			repeatableContainers – the repeatable containers that may be used by the element annotations or the meta-annotations 
			annotationFilter – an annotation filter used to restrict the annotations considered
        Returns:
            a MergedAnnotations instance containing the merged annotations for the supplied element
	 */
	// 代码10
	static MergedAnnotations from(AnnotatedElement element,                      // 待搜索注解所在的AnnotatedElement对象
								  SearchStrategy searchStrategy,                 // SearchStrategy.INHERITED_ANNOTATIONS,
								  RepeatableContainers repeatableContainers,     // NoRepeatableContainers      or StandardRepeatableContainers
								  AnnotationFilter annotationFilter) {           // AnnotationFilter.PLAIN

		Assert.notNull(repeatableContainers, "RepeatableContainers must not be null");
		Assert.notNull(annotationFilter, "AnnotationFilter must not be null");
		return TypeMappedAnnotations.from(element, searchStrategy, repeatableContainers, annotationFilter);  // 见TypeMappedAnnotations代码5
	}
 




 

}
