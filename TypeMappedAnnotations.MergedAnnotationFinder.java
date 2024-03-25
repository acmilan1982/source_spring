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

 
final class TypeMappedAnnotations implements MergedAnnotations {

	 


	/**
	 *  AnnotationsProcessor that finds a single MergedAnnotation.
	 */
	private class MergedAnnotationFinder<A extends Annotation>
			implements AnnotationsProcessor<Object, MergedAnnotation<A>> {

		// 注解的全限定类名，如：org.springframework.context.annotation.Configuration		
		private final Object requiredType;

		@Nullable
		private final Predicate<? super MergedAnnotation<A>> predicate;

		private final MergedAnnotationSelector<A> selector;

		@Nullable
		private MergedAnnotation<A> result;

		MergedAnnotationFinder(Object requiredType,                                               // 注解的全限定类名，如：org.springframework.context.annotation.Configuration
		                       @Nullable Predicate<? super MergedAnnotation<A>> predicate,        // null
				               @Nullable MergedAnnotationSelector<A> selector) {                  // new FirstDirectlyDeclared()

			this.requiredType = requiredType;
			this.predicate = predicate;
			this.selector = (selector != null ? selector : MergedAnnotationSelectors.nearest());
		}



		/**

		*/
		// 代码1
		@Override
		@Nullable
		public MergedAnnotation<A> doWithAggregate(Object context, int aggregateIndex) {
			return this.result;
		}




		/**
             
		     当前方法被 AnnotationsScanner 的 processElement 调用
		*/
		// 代码5
		@Override
		@Nullable
		public MergedAnnotation<A> doWithAnnotations(Object type,                 // 待搜索的注解，如org.springframework.context.annotation.Configuration
													 int aggregateIndex,          // 0
													 @Nullable Object source,     // 注解所在的AnnotatedElement对象，比如class/Method
													 Annotation[] annotations) {  // source上的所有Annotation对象

            
			// 遍历当前AnnotatedElement对象上的所有注解											
			for (Annotation annotation : annotations) {
				/*
				 *   annotationFilter是外部类TypeMappedAnnotations的成员变量：
				 *   private final AnnotationFilter annotationFilter;  // AnnotationFilter PLAIN = packages("java.lang", "org.springframework.lang");
				 */
				if (annotation != null && !annotationFilter.matches(annotation)) {
					MergedAnnotation<A> result = process(type, aggregateIndex, source, annotation);  // 见代码6
					if (result != null) {
						return result;
					}
				}
			}
			return null;
		}


		/**

		*/
		// 代码6
		@Nullable
		private MergedAnnotation<A> process(Object type,                // 待搜索的注解，如org.springframework.context.annotation.Configuration
											int aggregateIndex,         // 0
											@Nullable Object source,    // 注解所在的AnnotatedElement对象，比如class/Method
											Annotation annotation) {    // source上的某个Annotation对象(会遍历所有Annotation)

			Annotation[] repeatedAnnotations = repeatableContainers.findRepeatedAnnotations(annotation);
			if (repeatedAnnotations != null) {
				return doWithAnnotations(type, aggregateIndex, source, repeatedAnnotations);
			}


			/*
			 *  为当前注解创建AnnotationTypeMappings对象，
			 *  当前注解，包括其元注解，元注解的元注解，即整个层次结构上的每个注解对象，都会创建对应的AnnotationTypeMapping，
			 *  AnnotationTypeMappings持有这些AnnotationTypeMapping
			 * 
			 */
			AnnotationTypeMappings mappings = AnnotationTypeMappings.forAnnotationType(annotation.annotationType(),    // 见AnnotationTypeMappings代码5
																					   repeatableContainers, 
																					   annotationFilter);  // 外部类TypeMappedAnnotations的属性：AnnotationFilter PLAIN 

																					   

			for (int i = 0; i < mappings.size(); i++) {
				AnnotationTypeMapping mapping = mappings.get(i);
				// isMappingForType判断AnnotationTypeMapping代表的注解，是否与待搜索的注解一致
				if (isMappingForType(mapping, annotationFilter, this.requiredType)) {                                               // 见外部类 TypeMappedAnnotations 代码15


                    // 创建TypeMappedAnnotation
					MergedAnnotation<A> candidate = TypeMappedAnnotation.createIfPossible(mapping,                                  // 见TypeMappedAnnotation代码5
																						  source,                                   // 注解所在的AnnotatedElement对象，比如class/Method
																						  annotation,                               // source上的某个Annotation对象，比如@RequestMapping
																						  aggregateIndex, 
																						  IntrospectionFailureLogger.INFO);
					if (candidate != null && (this.predicate == null || this.predicate.test(candidate))) {
						if (this.selector.isBestCandidate(candidate)) {
							return candidate;
						}
						updateLastResult(candidate);
					}
				}
			}

			return null;
		}


 

		private void updateLastResult(MergedAnnotation<A> candidate) {
			MergedAnnotation<A> lastResult = this.result;
			this.result = (lastResult != null ? this.selector.select(lastResult, candidate) : candidate);
		}

		@Override
		@Nullable
		public MergedAnnotation<A> finish(@Nullable MergedAnnotation<A> result) {
			return (result != null ? result : this.result);
		}
	}

 
}
