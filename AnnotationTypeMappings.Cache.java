
package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Provides {@link AnnotationTypeMapping} information for a single source
 * annotation type. Performs a recursive breadth first crawl of all
 * meta-annotations to ultimately provide a quick way to map the attributes of
 * a root {@link Annotation}.
 *
 * <p>Supports convention based merging of meta-annotations as well as implicit
 * and explicit {@link AliasFor @AliasFor} aliases. Also provides information
 * about mirrored attributes.
 *
 * <p>This class is designed to be cached so that meta-annotations only need to
 * be searched once, regardless of how many times they are actually used.
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 5.2
 * @see AnnotationTypeMapping
 */
final class AnnotationTypeMappings {
 
	/**
	 * Cache created per {@link AnnotationFilter}.
	 */
	private static class Cache {

		private final RepeatableContainers repeatableContainers;

		private final AnnotationFilter filter;

		private final Map<Class<? extends Annotation>, AnnotationTypeMappings> mappings;

		/**
			Create a cache instance with the specified filter.
			Params:
			filter – the annotation filter
		*/
		// 构造方法
		Cache(RepeatableContainers repeatableContainers, AnnotationFilter filter) {
			this.repeatableContainers = repeatableContainers;
			this.filter = filter;
			this.mappings = new ConcurrentReferenceHashMap<>();
		}

		/**
			Get or create AnnotationTypeMappings for the specified annotation type.
			Params:
			annotationType – the annotation type visitedAnnotationTypes – the set of annotations that we have already visited; used to avoid infinite recursion for recursive annotations which some JVM languages support (such as Kotlin)
			Returns:
			a new or existing AnnotationTypeMappings instance
		*/
		// 代码1
		AnnotationTypeMappings get(Class<? extends Annotation> annotationType,                        // 注解的Class对象，比如：interface org.springframework.context.annotation.ComponentScan
				                   Set<Class<? extends Annotation>> visitedAnnotationTypes) {         // new HashSet<>()

			return this.mappings.computeIfAbsent(annotationType, key -> createMappings(key, visitedAnnotationTypes));
		}



		/**
 
		*/
		// 代码2
		private AnnotationTypeMappings createMappings(Class<? extends Annotation> annotationType,
				                                      Set<Class<? extends Annotation>> visitedAnnotationTypes) {

			return new AnnotationTypeMappings(this.repeatableContainers, this.filter, annotationType, visitedAnnotationTypes);
		}
	}
}
