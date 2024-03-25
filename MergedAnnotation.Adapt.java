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
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;

/**
	A single merged annotation returned from a MergedAnnotations collection. 
	Presents a view onto an annotation where attribute values may have been "merged" from different source values.

	Attribute values may be accessed using the various get methods. For example, to access an int attribute the getInt(String) method would be used.

	Note that attribute values are not converted when accessed. For example, it is not possible to call getString(String) if the underlying attribute is an int. 
	The only exception to this rule is Class and Class[] values which may be accessed as String and String[] respectively to prevent potential early class initialization.

	If necessary, a MergedAnnotation can be synthesized back into an actual Annotation.

	

	Since:
	5.2
	See Also:
	MergedAnnotations, MergedAnnotationPredicates
	Author:
	Phillip Webb, Juergen Hoeller, Sam Brannen
	Type parameters:
	<A> – the annotation type
 */
public interface MergedAnnotation<A extends Annotation> {

 

	/**
	 * Adaptations that can be applied to attribute values when creating
	 * {@linkplain MergedAnnotation#asMap(Adapt...) Maps} or
	 * {@link MergedAnnotation#asAnnotationAttributes(Adapt...) AnnotationAttributes}.
	 */
	enum Adapt {

		/**
		 * Adapt class or class array attributes to strings.
		 */
		CLASS_TO_STRING,

		/**
		 * Adapt nested annotation or annotation arrays to maps rather
		 * than synthesizing the values.
		 */
		ANNOTATION_TO_MAP;

		protected final boolean isIn(Adapt... adaptations) {
			for (Adapt candidate : adaptations) {
				if (candidate == this) {
					return true;
				}
			}
			return false;
		}

		/**
			Factory method to create an MergedAnnotation.Adapt array from a set of boolean flags.
			Params:
			   classToString – if CLASS_TO_STRING is included 
			   annotationsToMap – if ANNOTATION_TO_MAP is included
			Returns:
			   a new MergedAnnotation.Adapt array
		 */
		// 代码1
		public static Adapt[] values(boolean classToString, 
		                             boolean annotationsToMap) {
			EnumSet<Adapt> result = EnumSet.noneOf(Adapt.class);
			addIfTrue(result, Adapt.CLASS_TO_STRING, classToString);
			addIfTrue(result, Adapt.ANNOTATION_TO_MAP, annotationsToMap);
			return result.toArray(new Adapt[0]);
		}

		// 如果第三个参数是true,第二个参数加到第一个集合中
		// 代码2
		private static <T> void addIfTrue(Set<T> result, 
										  T value, 
										  boolean test) {
			if (test) {
				result.add(value);
			}
		}
	}

}
