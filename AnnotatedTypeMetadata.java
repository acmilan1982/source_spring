/*
 * Copyright 2002-2019 the original author or authors.
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
import java.util.Map;

import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.core.annotation.MergedAnnotationCollectors;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotationSelectors;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;

/**
 * 
	Defines access to the annotations of a specific type (class or method), in a form that does not necessarily require the class-loading.

	Since:
	  4.0
	See Also:
	  AnnotationMetadata, MethodMetadata
	Author:
	  Juergen Hoeller, Mark Fisher, Mark Pollack, Chris Beams, Phillip Webb, Sam Brannen

	 定义了访问指定类型(类/方法)注解的方式，从某种形式来说，可以不加载类，就读到注解 
 * 
 */
public interface AnnotatedTypeMetadata {


    /**
		Return annotation details based on the direct annotations of the underlying element.
		Returns:
		merged annotations based on the direct annotations
		Since:
		5.2
	 * 
	 */
	MergedAnnotations getAnnotations();
 

	/**
	 *  
		Determine whether the underlying element has an annotation or meta-annotation of the given type defined.
		If this method returns true, then getAnnotationAttributes will return a non-null Map.
		Params:
		annotationName – the fully qualified class name of the annotation type to look for
		Returns:
		whether a matching annotation is defined

		当前element上是否包含参数指定的注解或元注解
     * 
	 */
	// 代码1
	default boolean isAnnotated(String annotationName) {
        //  getAnnotations() 由子类实现，见 StandardAnnotationMetadata 代码5
		return getAnnotations().isPresent(annotationName);
	}


	/**	
	 *  
		Retrieve the attributes of the annotation of the given type, if any (i.e. if defined on the underlying element, as direct annotation or meta-annotation), 
		also taking attribute overrides on composed annotations into account.
		Params:
		annotationName – the fully qualified class name of the annotation type to look for
		Returns:
		a Map of attributes, with the attribute name as key (e.g. "value") and the defined attribute value as Map value. This return value will be null if no matching annotation is defined.

		获取指定annotation的属性
     * 
	 */
	// 代码2
	default Map<String, Object> getAnnotationAttributes(String annotationName) {
		return getAnnotationAttributes(annotationName, false);    // 见子类StandardAnnotationMetadata代码15
	}
	 
	/**
	 *  
		Retrieve the attributes of the annotation of the given type, 
		if any (i.e. if defined on the underlying element, as direct annotation or meta-annotation), 
		also taking attribute overrides on composed annotations into account.
		Params:
		annotationName – the fully qualified class name of the annotation type to look for classValuesAsString – whether to convert class references to String class names for exposure as values in the returned Map, instead of Class references which might potentially have to be loaded first
		Returns:
		a Map of attributes, with the attribute name as key (e.g. "value") and the defined attribute value as Map value. This return value will be null if no matching annotation is defined.

		获取给定注解类型的属性值
     * 
	 */
	// 代码3
	@Nullable
	default Map<String, Object> getAnnotationAttributes(String annotationName,          // 注解的全限定类名，如：org.springframework.context.annotation.Configuration
			                                            boolean classValuesAsString) {  // false

        /*
		 *  getAnnotations()由子类StandardAnnotationMetadata实现，
		 *  获取MergedAnnotations对象，实现类为TypeMappedAnnotations
		 * 
		 */
		MergedAnnotation<Annotation> annotation = getAnnotations().get(annotationName,                                          // getAnnotations() 见子类 StandardAnnotationMetadata代码16
				                                                       null,                                                    // get方法见TypeMappedAnnotations代码12
                                                                       MergedAnnotationSelectors.firstDirectlyDeclared());      // new FirstDirectlyDeclared()
		if (!annotation.isPresent()) {
			return null;
		}

		// annotation：MergedAnnotation对象，实现类为：TypeMappedAnnotation
		/*
		 * 
		 * 
		 */
		return annotation.asAnnotationAttributes(Adapt.values(classValuesAsString, true));  // asAnnotationAttributes见TypeMappedAnnotation父类AbstractMergedAnnotation代码5
	}
}
