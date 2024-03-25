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

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Abstract base class for {@link MergedAnnotation} implementations.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 5.2
 * @param <A> the annotation type
 */
abstract class AbstractMergedAnnotation<A extends Annotation> implements MergedAnnotation<A> {

	@Nullable
	private volatile A synthesizedAnnotation;


	
	
 	/**
	 *  
		From interface:
		MergedAnnotation Create a new mutable AnnotationAttributes instance from this merged annotation.
		The adaptations may be used to change the way that values are added.
		Specified by:
		  asAnnotationAttributes in interface MergedAnnotation
		Params:
		  adaptations – the adaptations that should be applied to the annotation values
		Returns:
		  an immutable map containing the attributes and values
     * 
	 */
	// 代码5
	@Override
	public AnnotationAttributes asAnnotationAttributes(Adapt... adaptations) {
		return asMap(mergedAnnotation -> new AnnotationAttributes(mergedAnnotation.getType()), adaptations);  // asMap见子类TypeMappedAnnotation代码10
	}
 



	/**
	 * 
		From interface:
		MergedAnnotation Create a type-safe synthesized version of this merged annotation that can be used directly in code.

		The result is synthesized using a JDK Proxy and as a result may incur a computational cost when first invoked.

		If this merged annotation was created from an annotation instance, that annotation will be returned unmodified if it is not synthesizable. 
		An annotation is considered synthesizable if one of the following is true.
			The annotation declares attributes annotated with @AliasFor.
			The annotation is a composed annotation that relies on convention-based annotation attribute overrides in meta-annotations.
			The annotation declares attributes that are annotations or arrays of annotations that are themselves synthesizable.
		Specified by:
		    synthesize in interface MergedAnnotation
		Returns:
		    a synthesized version of the annotation or the original annotation unmodifie
	 * 
	 */
	// 代码10
	@Override
	public Optional<A> synthesize(Predicate<? super MergedAnnotation<A>> condition)
			throws NoSuchElementException {

		return (condition.test(this) ? Optional.of(synthesize()) : Optional.empty());
	}


	/**
	 * 
		From interface:
		MergedAnnotation Create a type-safe synthesized version of this merged annotation that can be used directly in code.

		The result is synthesized using a JDK Proxy and as a result may incur a computational cost when first invoked.

		If this merged annotation was created from an annotation instance, that annotation will be returned unmodified if it is not synthesizable. 
		An annotation is considered synthesizable if one of the following is true.
			The annotation declares attributes annotated with @AliasFor.
			The annotation is a composed annotation that relies on convention-based annotation attribute overrides in meta-annotations.
			The annotation declares attributes that are annotations or arrays of annotations that are themselves synthesizable.
		Specified by:
		    synthesize in interface MergedAnnotation
		Returns:
		    a synthesized version of the annotation or the original annotation unmodifie
	 * 
	 */
	// 代码11
	public A synthesize() {
		if (!isPresent()) {
			throw new NoSuchElementException("Unable to synthesize missing annotation");
		}
		A synthesized = this.synthesizedAnnotation;
		if (synthesized == null) {
			synthesized = createSynthesized();
			this.synthesizedAnnotation = synthesized;
		}
		return synthesized;
	}



	/**
	 * 
		Get an optional attribute value from the annotation.
		
		Params:
		  attributeName – the attribute name 
		  type – the attribute type. 
		         Must be compatible with the underlying attribute type or Object.class.

		Returns:
		  an optional value or Optional.empty() if there is no matching attribute
	 */
	@Override
	public <T> Optional<T> getValue(String attributeName, Class<T> type) {
		return Optional.ofNullable(getAttributeValue(attributeName, type));   // 见子类 TypeMappedAnnotation 代码60
	}





}
