
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
	Provides AnnotationTypeMapping information for a single source annotation type. 
	Performs a recursive breadth first crawl of all meta-annotations to ultimately provide a quick way to map the attributes of a root Annotation.

	Supports convention based merging of meta-annotations as well as implicit and explicit @AliasFor aliases. 
	Also provides information about mirrored attributes.

	This class is designed to be cached so that meta-annotations only need to be searched once, 
	regardless of how many times they are actually used.
	Since:
	  5.2
	See Also:
	  AnnotationTypeMapping
	Author:
	  Phillip Webb, Sam Brannen
 */
final class AnnotationTypeMappings {

	private static final IntrospectionFailureLogger failureLogger = IntrospectionFailureLogger.DEBUG;

	private static final Map<AnnotationFilter, Cache> standardRepeatablesCache = new ConcurrentReferenceHashMap<>();

	private static final Map<AnnotationFilter, Cache> noRepeatablesCache = new ConcurrentReferenceHashMap<>();


	private final RepeatableContainers repeatableContainers;

	private final AnnotationFilter filter;

	// 持有当前注解对应的AnnotationTypeMapping，以及每个元注解对应的AnnotationTypeMapping
	// 同时，这些 AnnotationTypeMapping 之间保持着关联
	private final List<AnnotationTypeMapping> mappings;

	/**

	 */
	// 构造方法
	private AnnotationTypeMappings(RepeatableContainers repeatableContainers,
			                       AnnotationFilter filter, 
								   Class<? extends Annotation> annotationType,                  // AnnotatedElement对象上的某个注解，比如： org.springframework.web.bind.annotation.PostMapping，
								                                                                //                                        org.springframework.context.annotation.ComponentScan
			                       Set<Class<? extends Annotation>> visitedAnnotationTypes) {   // new HashSet<>()

		this.repeatableContainers = repeatableContainers;
		this.filter = filter;
		this.mappings = new ArrayList<>();
		addAllMappings(annotationType, visitedAnnotationTypes);             // 见代码1
		this.mappings.forEach(AnnotationTypeMapping::afterAllMappingsSet);
	}

	/**
 
	 */
	// 代码1
	private void addAllMappings(Class<? extends Annotation> annotationType,                  // AnnotatedElement对象上的某个注解，比如： org.springframework.web.bind.annotation.PostMapping，
	                                                                                        //                                        org.springframework.context.annotation.ComponentScan
			                    Set<Class<? extends Annotation>> visitedAnnotationTypes) {

		Deque<AnnotationTypeMapping> queue = new ArrayDeque<>();

		// 为当前注解对象创建AnnotationTypeMapping
		addIfPossible(queue,                                            // 见代码2
				 	  null, 
				 	  annotationType, 
					  null, 
					  visitedAnnotationTypes);

		while (!queue.isEmpty()) {
			AnnotationTypeMapping mapping = queue.removeFirst();
			this.mappings.add(mapping);
			// 为元注解创建对应的 AnnotationTypeMapping
			addMetaAnnotationsToQueue(queue, mapping);     // 见代码3
		}
	}

	/**
 
	 */
	// 代码2
	private void addIfPossible(Deque<AnnotationTypeMapping> queue, 
							  @Nullable AnnotationTypeMapping source,      // 如果是root，该值为：null/如果是元注解，则source为元注解所在的子注解AnnotationTypeMapping对象，如PostMapping对应的AnnotationTypeMapping
							  Class<? extends Annotation> annotationType,  // 注解的Class对象，比如: interface org.springframework.web.bind.annotation.PostMapping/元注解的class对象，如：@RequestMapping
							  @Nullable Annotation ann,                    // 如果是root，该值为：null/元注解
							  Set<Class<? extends Annotation>> visitedAnnotationTypes) {

		try {
			queue.addLast(new AnnotationTypeMapping(source, annotationType, ann, visitedAnnotationTypes));  // 
		}
		catch (Exception ex) {
			AnnotationUtils.rethrowAnnotationConfigurationException(ex);
			if (failureLogger.isEnabled()) {
				failureLogger.log("Failed to introspect meta-annotation " + annotationType.getName(),
						(source != null ? source.getAnnotationType() : null), ex);
			}
		}
	}

	/**
 
	 */
	// 代码3
	private void addMetaAnnotationsToQueue(Deque<AnnotationTypeMapping> queue, 
	                                       AnnotationTypeMapping source) {
		// 搜索AnnotationTypeMapping所代表Annotation的所有直接出现元注解
		Annotation[] metaAnnotations = AnnotationsScanner.getDeclaredAnnotations(source.getAnnotationType(), false);  // 见AnnotationsScanner代码10

		// 遍历所有元注解
		for (Annotation metaAnnotation : metaAnnotations) {
			if (!isMappable(source, metaAnnotation)) {
				continue;
			}
			Annotation[] repeatedAnnotations = this.repeatableContainers.findRepeatedAnnotations(metaAnnotation);
			if (repeatedAnnotations != null) {
				for (Annotation repeatedAnnotation : repeatedAnnotations) {
					if (!isMappable(source, repeatedAnnotation)) {
						continue;
					}
					addIfPossible(queue, source, repeatedAnnotation);
				}
			}
			else {


				addIfPossible(queue, source, metaAnnotation);     // 见代码4
			}
		}
	}	
 
	/**
 
	 */
	// 代码4
	private void addIfPossible(Deque<AnnotationTypeMapping> queue, 
							   AnnotationTypeMapping source,  // 元注解所在的子注解AnnotationTypeMapping对象，如PostMapping对应的AnnotationTypeMapping
							   Annotation ann) {              // 元注解，如@RequestMapping
		addIfPossible(queue, source, ann.annotationType(), ann, new HashSet<>());          // 见代码2
	}



	/**
		Create AnnotationTypeMappings for the specified annotation type.
		Params:
			annotationType – the source annotation type 
			repeatableContainers – the repeatable containers that may be used by the meta-annotations 
			annotationFilter – the annotation filter used to limit which annotations are considered
		Returns:
		    type mappings for the annotation type

	 */
	// 代码5
	static AnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType,   // Annotation的Clas对象，annotation.annotationType()
													RepeatableContainers repeatableContainers, 
													AnnotationFilter annotationFilter) {          // AnnotationFilter PLAIN 

		return forAnnotationType(annotationType, repeatableContainers, annotationFilter, new HashSet<>());  // 见代码6
	}	
 

 	/**
		Create AnnotationTypeMappings for the specified annotation type.

		Params:
			annotationType – the source annotation type 
			repeatableContainers – the repeatable containers that may be used by the meta-annotations 
			annotationFilter – the annotation filter used to limit which annotations are considered 
			visitedAnnotationTypes – the set of annotations that we have already visited; used to avoid infinite recursion for recursive annotations which some JVM languages support (such as Kotlin)

		Returns:
		type mappings for the annotation type
	 */
	// 代码6
	private static AnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType,       // Annotation的Clas对象，annotation.annotationType()，注解的Class对象，比如：interface org.springframework.context.annotation.ComponentScan
															RepeatableContainers repeatableContainers,        // RepeatableContainers.none()
															AnnotationFilter annotationFilter,                // AnnotationFilter PLAIN 
															Set<Class<? extends Annotation>> visitedAnnotationTypes) {  // new HashSet<>()

		if (repeatableContainers == RepeatableContainers.standardRepeatables()) {
			return standardRepeatablesCache.computeIfAbsent(annotationFilter,
					key -> new Cache(repeatableContainers, key)).get(annotationType, visitedAnnotationTypes);
		}

		if (repeatableContainers == RepeatableContainers.none()) {
 
			 
            /*
			 *   noRepeatablesCache: 当前类的成员变量：
			 *   private static final Map<AnnotationFilter, Cache> noRepeatablesCache = new ConcurrentReferenceHashMap<>();
			 */
			return noRepeatablesCache.computeIfAbsent(annotationFilter,
					                                  key -> new Cache(repeatableContainers, key)).get(annotationType, visitedAnnotationTypes);
		}

		return new AnnotationTypeMappings(repeatableContainers, annotationFilter, annotationType, visitedAnnotationTypes);
	}



	/**
		Get the total number of contained mappings.
		Returns:
		the total number of mappings
	*/
	// 代码10
	int size() {
		return this.mappings.size();
	}

}
