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
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotationTypeMapping.MirrorSets.MirrorSet;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
	General utility methods for working with annotations, handling meta-annotations, bridge methods (which the compiler generates for generic declarations) as well as super methods (for optional annotation inheritance).
	通用的工具方法，用来处理注解，元注解，桥接方法及父类的方法

	Note that most of the features of this class are not provided by the JDK's introspection facilities themselves.
    请注意，这个类的大多数功能并不是由JDK的内省工具本身提供的

	As a general rule for runtime-retained application annotations (e.g. for transaction control, authorization, or service exposure), 
	always use the lookup methods on this class (e.g. findAnnotation(Method, Class) or getAnnotation(Method, Class)) instead of the plain annotation lookup methods in the JDK. 
	You can still explicitly choose between a get lookup on the given class level only (getAnnotation(Method, Class)) and 
	a find lookup in the entire inheritance hierarchy of the given method (findAnnotation(Method, Class)).
	作为读取运行时保留的程序注解通用规则，使用当前类的find或get方法，而不是jdk的原生注解查找方法。
	你可以使用get方法，在给定的类级别中，getAnnotation(Method, Class)
	或者find方法，在整个类的继承体系中，findAnnotation(Method, Class)
	
	
	Terminology
		The terms directly present, indirectly present, and present have the same meanings as defined in the class-level javadoc for AnnotatedElement (in Java 8).
		An annotation is meta-present on an element if the annotation is declared as a meta-annotation on some other annotation which is present on the element. 
		Annotation A is meta-present on another annotation if A is either directly present or meta-present on the other annotation.
	
	Meta-annotation Support
		Most find*() methods and some get*() methods in this class provide support for finding annotations used as meta-annotations. 
		Consult the javadoc for each method in this class for details. 
		For fine-grained support for meta-annotations with attribute overrides in composed annotations, consider using AnnotatedElementUtils's more specific methods instead.
	    大多数find*()方法和部分get*()方法，都支持查找作为元注解的注解
		对于在组合注释中使用属性重写的元注释的细粒度支持，请考虑使用AnnotatedElementUtils的更具体的方法。

	Attribute Aliases
		All public methods in this class that return annotations, arrays of annotations, 
		or AnnotationAttributes transparently support attribute aliases configured via @AliasFor. 
		Consult the various synthesizeAnnotation*(..) methods for details.
	
	Search Scope
	   The search algorithms used by methods in this class stop searching for an annotation once the first annotation of the specified type has been found. 
	   As a consequence, additional annotations of the specified type will be silently ignored.
	   一旦找到指定类型的第一个注释，此类中的方法所使用的搜索算法就会停止搜索注释。
       因此，指定类型的其他注释将被忽略。
	Since:
	  2.0
	See Also:
	  AliasFor, AnnotationAttributes, AnnotatedElementUtils, BridgeMethodResolver, AnnotatedElement.getAnnotations(), AnnotatedElement.getAnnotation(Class), AnnotatedElement.getDeclaredAnnotations()
	Author:
	  Rob Harrop, Juergen Hoeller, Sam Brannen, Mark Fisher, Chris Beams, Phillip Webb, Oleg Zhurakousky
 */
public abstract class AnnotationUtils {

	/**
	 * The attribute name for annotations with a single element.
	 */
	public static final String VALUE = MergedAnnotation.VALUE;

	private static final AnnotationFilter JAVA_LANG_ANNOTATION_FILTER =
			AnnotationFilter.packages("java.lang.annotation");

	private static final Map<Class<? extends Annotation>, Map<String, DefaultValueHolder>> defaultValuesCache =
			new ConcurrentReferenceHashMap<>();


    /**
        Determine whether the given class is a candidate for carrying one of the specified annotations (at type, method or field level).
        Params:
          clazz – the class to introspect annotationTypes – the searchable annotation types
        Returns:
          false if the class is known to have no such annotations at any level; true otherwise. Callers will usually perform full method/field introspection if true is being returned here.
        Since:
          5.2
        See Also:
          isCandidateClass(Class, Class), isCandidateClass(Class, String)
    */
    // 代码5
	public static boolean isCandidateClass(Class<?> clazz, Collection<Class<? extends Annotation>> annotationTypes) {
		for (Class<? extends Annotation> annotationType : annotationTypes) {
			if (isCandidateClass(clazz, annotationType)) {
				return true;
			}
		}
		return false;
	}

    /**
Determine whether the given class is a candidate for carrying the specified annotation (at type, method or field level).
Params:
clazz – the class to introspect annotationType – the searchable annotation type
Returns:
false if the class is known to have no such annotations at any level; true otherwise. Callers will usually perform full method/field introspection if true is being returned here.
Since:
5.2
See Also:
isCandidateClass(Class, String)
    */
    // 代码6
	public static boolean isCandidateClass(Class<?> clazz, Class<? extends Annotation> annotationType) {
		return isCandidateClass(clazz, annotationType.getName());
	}

    /**
Determine whether the given class is a candidate for carrying the specified annotation (at type, method or field level).
Params:
clazz – the class to introspect annotationName – the fully-qualified name of the searchable annotation type
Returns:
false if the class is known to have no such annotations at any level; true otherwise. Callers will usually perform full method/field introspection if true is being returned here.
Since:
5.2
See Also:
isCandidateClass(Class, Class
    */
    // 代码7
	public static boolean isCandidateClass(Class<?> clazz, String annotationName) {
		if (annotationName.startsWith("java.")) {
			return true;
		}
		if (AnnotationsScanner.hasPlainJavaAnnotationsOnly(clazz)) {
			return false;
		}
		return true;
	}

	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the supplied
	 * annotation: either the given annotation itself or a direct meta-annotation
	 * thereof.
	 * <p>Note that this method supports only a single level of meta-annotations.
	 * For support for arbitrary levels of meta-annotations, use one of the
	 * {@code find*()} methods instead.
	 * @param annotation the Annotation to check
	 * @param annotationType the annotation type to look for, both locally and as a meta-annotation
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 4.0
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	public static <A extends Annotation> A getAnnotation(Annotation annotation, Class<A> annotationType) {
		// Shortcut: directly present on the element, with no merging needed?
		if (annotationType.isInstance(annotation)) {
			return synthesizeAnnotation((A) annotation, annotationType);
		}
		// Shortcut: no searchable annotations to be found on plain Java classes and core Spring types...
		if (AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotation)) {
			return null;
		}
		// Exhaustive retrieval of merged annotations...
		return MergedAnnotations.from(annotation, new Annotation[] {annotation}, RepeatableContainers.none())
				.get(annotationType).withNonMergedAttributes()
				.synthesize(AnnotationUtils::isSingleLevelPresent).orElse(null);
	}

	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the supplied
	 * {@link AnnotatedElement}, where the annotation is either <em>present</em> or
	 * <em>meta-present</em> on the {@code AnnotatedElement}.
	 * <p>Note that this method supports only a single level of meta-annotations.
	 * For support for arbitrary levels of meta-annotations, use
	 * {@link #findAnnotation(AnnotatedElement, Class)} instead.
	 * @param annotatedElement the {@code AnnotatedElement} from which to get the annotation
	 * @param annotationType the annotation type to look for, both locally and as a meta-annotation
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 3.1
	 */
	@Nullable
	public static <A extends Annotation> A getAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
		// Shortcut: directly present on the element, with no merging needed?
		if (AnnotationFilter.PLAIN.matches(annotationType) ||
				AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotatedElement)) {
			return annotatedElement.getAnnotation(annotationType);
		}
		// Exhaustive retrieval of merged annotations...
		return MergedAnnotations.from(annotatedElement, SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none())
				.get(annotationType).withNonMergedAttributes()
				.synthesize(AnnotationUtils::isSingleLevelPresent).orElse(null);
	}

	private static <A extends Annotation> boolean isSingleLevelPresent(MergedAnnotation<A> mergedAnnotation) {
		int distance = mergedAnnotation.getDistance();
		return (distance == 0 || distance == 1);
	}

	/**
	 * Get a single {@link Annotation} of {@code annotationType} from the
	 * supplied {@link Method}, where the annotation is either <em>present</em>
	 * or <em>meta-present</em> on the method.
	 * <p>Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * <p>Note that this method supports only a single level of meta-annotations.
	 * For support for arbitrary levels of meta-annotations, use
	 * {@link #findAnnotation(Method, Class)} instead.
	 * @param method the method to look for annotations on
	 * @param annotationType the annotation type to look for
	 * @return the first matching annotation, or {@code null} if not found
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 * @see #getAnnotation(AnnotatedElement, Class)
	 */
	@Nullable
	public static <A extends Annotation> A getAnnotation(Method method, Class<A> annotationType) {
		Method resolvedMethod = BridgeMethodResolver.findBridgedMethod(method);
		return getAnnotation((AnnotatedElement) resolvedMethod, annotationType);
	}

	/**
	 * Get all {@link Annotation Annotations} that are <em>present</em> on the
	 * supplied {@link AnnotatedElement}.
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * @param annotatedElement the Method, Constructor or Field to retrieve annotations from
	 * @return the annotations found, an empty array, or {@code null} if not
	 * resolvable (e.g. because nested Class values in annotation attributes
	 * failed to resolve at runtime)
	 * @since 4.0.8
	 * @see AnnotatedElement#getAnnotations()
	 * @deprecated as of 5.2 since it is superseded by the {@link MergedAnnotations} API
	 */
	@Deprecated
	@Nullable
	public static Annotation[] getAnnotations(AnnotatedElement annotatedElement) {
		try {
			return synthesizeAnnotationArray(annotatedElement.getAnnotations(), annotatedElement);
		}
		catch (Throwable ex) {
			handleIntrospectionFailure(annotatedElement, ex);
			return null;
		}
	}

	/**
	 * Get all {@link Annotation Annotations} that are <em>present</em> on the
	 * supplied {@link Method}.
	 * <p>Correctly handles bridge {@link Method Methods} generated by the compiler.
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * @param method the Method to retrieve annotations from
	 * @return the annotations found, an empty array, or {@code null} if not
	 * resolvable (e.g. because nested Class values in annotation attributes
	 * failed to resolve at runtime)
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod(Method)
	 * @see AnnotatedElement#getAnnotations()
	 * @deprecated as of 5.2 since it is superseded by the {@link MergedAnnotations} API
	 */
	@Deprecated
	@Nullable
	public static Annotation[] getAnnotations(Method method) {
		try {
			return synthesizeAnnotationArray(BridgeMethodResolver.findBridgedMethod(method).getAnnotations(), method);
		}
		catch (Throwable ex) {
			handleIntrospectionFailure(method, ex);
			return null;
		}
	}

	/**
	 * Get the <em>repeatable</em> {@linkplain Annotation annotations} of
	 * {@code annotationType} from the supplied {@link AnnotatedElement}, where
	 * such annotations are either <em>present</em>, <em>indirectly present</em>,
	 * or <em>meta-present</em> on the element.
	 * <p>This method mimics the functionality of Java 8's
	 * {@link java.lang.reflect.AnnotatedElement#getAnnotationsByType(Class)}
	 * with support for automatic detection of a <em>container annotation</em>
	 * declared via @{@link java.lang.annotation.Repeatable} (when running on
	 * Java 8 or higher) and with additional support for meta-annotations.
	 * <p>Handles both single annotations and annotations nested within a
	 * <em>container annotation</em>.
	 * <p>Correctly handles <em>bridge methods</em> generated by the
	 * compiler if the supplied element is a {@link Method}.
	 * <p>Meta-annotations will be searched if the annotation is not
	 * <em>present</em> on the supplied element.
	 * @param annotatedElement the element to look for annotations on
	 * @param annotationType the annotation type to look for
	 * @return the annotations found or an empty set (never {@code null})
	 * @since 4.2
	 * @see #getRepeatableAnnotations(AnnotatedElement, Class, Class)
	 * @see #getDeclaredRepeatableAnnotations(AnnotatedElement, Class, Class)
	 * @see AnnotatedElementUtils#getMergedRepeatableAnnotations(AnnotatedElement, Class)
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod
	 * @see java.lang.annotation.Repeatable
	 * @see java.lang.reflect.AnnotatedElement#getAnnotationsByType
	 * @deprecated as of 5.2 since it is superseded by the {@link MergedAnnotations} API
	 */
	@Deprecated
	public static <A extends Annotation> Set<A> getRepeatableAnnotations(AnnotatedElement annotatedElement,
			Class<A> annotationType) {

		return getRepeatableAnnotations(annotatedElement, annotationType, null);
	}

	/**
	 * Get the <em>repeatable</em> {@linkplain Annotation annotations} of
	 * {@code annotationType} from the supplied {@link AnnotatedElement}, where
	 * such annotations are either <em>present</em>, <em>indirectly present</em>,
	 * or <em>meta-present</em> on the element.
	 * <p>This method mimics the functionality of Java 8's
	 * {@link java.lang.reflect.AnnotatedElement#getAnnotationsByType(Class)}
	 * with additional support for meta-annotations.
	 * <p>Handles both single annotations and annotations nested within a
	 * <em>container annotation</em>.
	 * <p>Correctly handles <em>bridge methods</em> generated by the
	 * compiler if the supplied element is a {@link Method}.
	 * <p>Meta-annotations will be searched if the annotation is not
	 * <em>present</em> on the supplied element.
	 * @param annotatedElement the element to look for annotations on
	 * @param annotationType the annotation type to look for
	 * @param containerAnnotationType the type of the container that holds
	 * the annotations; may be {@code null} if a container is not supported
	 * or if it should be looked up via @{@link java.lang.annotation.Repeatable}
	 * when running on Java 8 or higher
	 * @return the annotations found or an empty set (never {@code null})
	 * @since 4.2
	 * @see #getRepeatableAnnotations(AnnotatedElement, Class)
	 * @see #getDeclaredRepeatableAnnotations(AnnotatedElement, Class)
	 * @see #getDeclaredRepeatableAnnotations(AnnotatedElement, Class, Class)
	 * @see AnnotatedElementUtils#getMergedRepeatableAnnotations(AnnotatedElement, Class, Class)
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod
	 * @see java.lang.annotation.Repeatable
	 * @see java.lang.reflect.AnnotatedElement#getAnnotationsByType
	 * @deprecated as of 5.2 since it is superseded by the {@link MergedAnnotations} API
	 */
	@Deprecated
	public static <A extends Annotation> Set<A> getRepeatableAnnotations(AnnotatedElement annotatedElement,
			Class<A> annotationType, @Nullable Class<? extends Annotation> containerAnnotationType) {

		RepeatableContainers repeatableContainers = (containerAnnotationType != null ?
				RepeatableContainers.of(annotationType, containerAnnotationType) :
				RepeatableContainers.standardRepeatables());

		return MergedAnnotations.from(annotatedElement, SearchStrategy.SUPERCLASS, repeatableContainers)
				.stream(annotationType)
				.filter(MergedAnnotationPredicates.firstRunOf(MergedAnnotation::getAggregateIndex))
				.map(MergedAnnotation::withNonMergedAttributes)
				.collect(MergedAnnotationCollectors.toAnnotationSet());
	}

	/**
	 * Get the declared <em>repeatable</em> {@linkplain Annotation annotations}
	 * of {@code annotationType} from the supplied {@link AnnotatedElement},
	 * where such annotations are either <em>directly present</em>,
	 * <em>indirectly present</em>, or <em>meta-present</em> on the element.
	 * <p>This method mimics the functionality of Java 8's
	 * {@link java.lang.reflect.AnnotatedElement#getDeclaredAnnotationsByType(Class)}
	 * with support for automatic detection of a <em>container annotation</em>
	 * declared via @{@link java.lang.annotation.Repeatable} (when running on
	 * Java 8 or higher) and with additional support for meta-annotations.
	 * <p>Handles both single annotations and annotations nested within a
	 * <em>container annotation</em>.
	 * <p>Correctly handles <em>bridge methods</em> generated by the
	 * compiler if the supplied element is a {@link Method}.
	 * <p>Meta-annotations will be searched if the annotation is not
	 * <em>present</em> on the supplied element.
	 * @param annotatedElement the element to look for annotations on
	 * @param annotationType the annotation type to look for
	 * @return the annotations found or an empty set (never {@code null})
	 * @since 4.2
	 * @see #getRepeatableAnnotations(AnnotatedElement, Class)
	 * @see #getRepeatableAnnotations(AnnotatedElement, Class, Class)
	 * @see #getDeclaredRepeatableAnnotations(AnnotatedElement, Class, Class)
	 * @see AnnotatedElementUtils#getMergedRepeatableAnnotations(AnnotatedElement, Class)
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod
	 * @see java.lang.annotation.Repeatable
	 * @see java.lang.reflect.AnnotatedElement#getDeclaredAnnotationsByType
	 * @deprecated as of 5.2 since it is superseded by the {@link MergedAnnotations} API
	 */
	@Deprecated
	public static <A extends Annotation> Set<A> getDeclaredRepeatableAnnotations(AnnotatedElement annotatedElement,
			Class<A> annotationType) {

		return getDeclaredRepeatableAnnotations(annotatedElement, annotationType, null);
	}

	/**
	 * Get the declared <em>repeatable</em> {@linkplain Annotation annotations}
	 * of {@code annotationType} from the supplied {@link AnnotatedElement},
	 * where such annotations are either <em>directly present</em>,
	 * <em>indirectly present</em>, or <em>meta-present</em> on the element.
	 * <p>This method mimics the functionality of Java 8's
	 * {@link java.lang.reflect.AnnotatedElement#getDeclaredAnnotationsByType(Class)}
	 * with additional support for meta-annotations.
	 * <p>Handles both single annotations and annotations nested within a
	 * <em>container annotation</em>.
	 * <p>Correctly handles <em>bridge methods</em> generated by the
	 * compiler if the supplied element is a {@link Method}.
	 * <p>Meta-annotations will be searched if the annotation is not
	 * <em>present</em> on the supplied element.
	 * @param annotatedElement the element to look for annotations on
	 * @param annotationType the annotation type to look for
	 * @param containerAnnotationType the type of the container that holds
	 * the annotations; may be {@code null} if a container is not supported
	 * or if it should be looked up via @{@link java.lang.annotation.Repeatable}
	 * when running on Java 8 or higher
	 * @return the annotations found or an empty set (never {@code null})
	 * @since 4.2
	 * @see #getRepeatableAnnotations(AnnotatedElement, Class)
	 * @see #getRepeatableAnnotations(AnnotatedElement, Class, Class)
	 * @see #getDeclaredRepeatableAnnotations(AnnotatedElement, Class)
	 * @see AnnotatedElementUtils#getMergedRepeatableAnnotations(AnnotatedElement, Class, Class)
	 * @see org.springframework.core.BridgeMethodResolver#findBridgedMethod
	 * @see java.lang.annotation.Repeatable
	 * @see java.lang.reflect.AnnotatedElement#getDeclaredAnnotationsByType
	 * @deprecated as of 5.2 since it is superseded by the {@link MergedAnnotations} API
	 */
	@Deprecated
	public static <A extends Annotation> Set<A> getDeclaredRepeatableAnnotations(AnnotatedElement annotatedElement,
			Class<A> annotationType, @Nullable Class<? extends Annotation> containerAnnotationType) {

		RepeatableContainers repeatableContainers = containerAnnotationType != null ?
				RepeatableContainers.of(annotationType, containerAnnotationType) :
				RepeatableContainers.standardRepeatables();

		return MergedAnnotations.from(annotatedElement, SearchStrategy.DIRECT, repeatableContainers)
				.stream(annotationType)
				.map(MergedAnnotation::withNonMergedAttributes)
				.collect(MergedAnnotationCollectors.toAnnotationSet());
	}

	/**
	 * Find a single {@link Annotation} of {@code annotationType} on the
	 * supplied {@link AnnotatedElement}.
	 * <p>Meta-annotations will be searched if the annotation is not
	 * <em>directly present</em> on the supplied element.
	 * <p><strong>Warning</strong>: this method operates generically on
	 * annotated elements. In other words, this method does not execute
	 * specialized search algorithms for classes or methods. If you require
	 * the more specific semantics of {@link #findAnnotation(Class, Class)}
	 * or {@link #findAnnotation(Method, Class)}, invoke one of those methods
	 * instead.
	 * @param annotatedElement the {@code AnnotatedElement} on which to find the annotation
	 * @param annotationType the annotation type to look for, both locally and as a meta-annotation
	 * @return the first matching annotation, or {@code null} if not found
	 * @since 4.2
	 */
	@Nullable
	public static <A extends Annotation> A findAnnotation(
			AnnotatedElement annotatedElement, @Nullable Class<A> annotationType) {

		if (annotationType == null) {
			return null;
		}

		// Shortcut: directly present on the element, with no merging needed?
		if (AnnotationFilter.PLAIN.matches(annotationType) ||
				AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotatedElement)) {
			return annotatedElement.getDeclaredAnnotation(annotationType);
		}

		// Exhaustive retrieval of merged annotations...
		return MergedAnnotations.from(annotatedElement, SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none())
				.get(annotationType).withNonMergedAttributes()
				.synthesize(MergedAnnotation::isPresent).orElse(null);
	}

	/**
		Find the first annotation of the specified annotationType within the annotation hierarchy above the supplied element, 
		merge that annotation's attributes with matching attributes from annotations in lower levels of the annotation hierarchy, 
		and synthesize the result back into an annotation of the specified annotationType.

		在所提供元素上方的注释层次结构中查找指定注释类型的第一个注释，
		将该注释的属性与来自注释层次结构的较低级别中的注释的匹配属性合并，
		并将结果合成为指定的annotationType的注释。

		@AliasFor semantics are fully supported, both within a single annotation and within the annotation hierarchy.
        @AliasFor语义在单个注释和注释层次结构中都完全受支持。

		This method follows find semantics as described in the class-level javadoc.
		该方法遵循类级javadoc中描述的find语义。

	
		Parameters:
		  element - the annotated element
		  annotationType - the annotation type to find
		Returns:
		  the merged, synthesized Annotation, or null if not found

	 */
	@Nullable
	public static <A extends Annotation> A findAnnotation(Method method, @Nullable Class<A> annotationType) {
		if (annotationType == null) {
			return null;
		}

		// Shortcut: directly present on the element, with no merging needed?
		if (AnnotationFilter.PLAIN.matches(annotationType) ||                 // 如果注解本身是普通注解，"java.lang", "org.springframework.lang"包下的注解
				AnnotationsScanner.hasPlainJavaAnnotationsOnly(method)) {     // 或者方法所在的类是jdk中的类
			return method.getDeclaredAnnotation(annotationType);              // 那么获取方法上直接出现的注解(不考虑类的继承体系)
		}

		/*
		     创建MergedAnnotations
		 *   当 MergedAnnotations 被创建后，并不会立刻就触发对 AnnotatedElement 的搜索
		 */
		// Exhaustive retrieval of merged annotations...
		return MergedAnnotations.from(method, SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none())   // 返回MergedAnnotations对象，如TypeMappedAnnotations  见MergedAnnotations代码5
								.get(annotationType)                                    // 返回MergedAnnotation对象，get方法见TypeMappedAnnotations代码7                  
								.withNonMergedAttributes()                              // 返回MergedAnnotation对象
								.synthesize(MergedAnnotation::isPresent).orElse(null);
	}

	/**
		Find a single Annotation of annotationType on the supplied Class, traversing its interfaces, annotations, and superclasses if the annotation is not directly present on the given class itself.
		在提供的类上查找annotationType类型的注释，如果注解不直接存在于给定类本身上，则遍历其接口、注解和超类。

		This method explicitly handles class-level annotations which are not declared as inherited as well as meta-annotations and annotations on interfaces.
		此方法显式处理未声明为继承的类级注释以及接口上的元注释和注释。

		The algorithm operates as follows:
		1. Search for the annotation on the given class and return it if found.
		2. Recursively search through all annotations that the given class declares.
		3. Recursively search through all interfaces that the given class declares.
		4. Recursively search through the superclass hierarchy of the given class.
		1.在给定类上搜索注释，如果找到则返回。
		2.递归搜索给定类声明的所有注释。
		3.递归搜索给定类声明的所有接口。
		4.通过给定类的超类层次结构进行递归搜索。

		Note: in this context, the term recursively means that the search process continues by returning to step #1 with the current interface, annotation, or superclass as the class to look for annotations on.
		注意：在这种情况下，这个术语递归地表示搜索过程继续进行，返回到步骤#1，将当前接口、注释或超类作为要查找注释的类。
		Params:
		clazz – the class to look for annotations on 
		annotationType – the type of annotation to look for
		Returns:
		the first matching annotation, or null if not found
	 */
	@Nullable
	// 代码10
	public static <A extends Annotation> A findAnnotation(Class<?> clazz, 
	                                                      @Nullable Class<A> annotationType) {
		if (annotationType == null) {
			return null;
		}

		// Shortcut: directly present on the element, with no merging needed?
		if (AnnotationFilter.PLAIN.matches(annotationType) ||
				AnnotationsScanner.hasPlainJavaAnnotationsOnly(clazz)) {
			A annotation = clazz.getDeclaredAnnotation(annotationType);
			if (annotation != null) {
				return annotation;
			}
			// For backwards compatibility, perform a superclass search with plain annotations
			// even if not marked as @Inherited: e.g. a findAnnotation search for @Deprecated
			Class<?> superclass = clazz.getSuperclass();
			if (superclass == null || superclass == Object.class) {
				return null;
			}
			return findAnnotation(superclass, annotationType);
		}

		// Exhaustive retrieval of merged annotations...
		return MergedAnnotations.from(clazz, SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none())      // 返回MergedAnnotations对象，如TypeMappedAnnotations  见MergedAnnotations代码5
				.get(annotationType)                                                                          // get方法见 TypeMappedAnnotations 代码7       
				.withNonMergedAttributes()            
				.synthesize(MergedAnnotation::isPresent).orElse(null);
	}

	/**
	 * Find the first {@link Class} in the inheritance hierarchy of the
	 * specified {@code clazz} (including the specified {@code clazz} itself)
	 * on which an annotation of the specified {@code annotationType} is
	 * <em>directly present</em>.
	 * <p>If the supplied {@code clazz} is an interface, only the interface
	 * itself will be checked; the inheritance hierarchy for interfaces will
	 * not be traversed.
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * <p>The standard {@link Class} API does not provide a mechanism for
	 * determining which class in an inheritance hierarchy actually declares
	 * an {@link Annotation}, so we need to handle this explicitly.
	 * @param annotationType the annotation type to look for
	 * @param clazz the class to check for the annotation on (may be {@code null})
	 * @return the first {@link Class} in the inheritance hierarchy that
	 * declares an annotation of the specified {@code annotationType},
	 * or {@code null} if not found
	 * @see Class#isAnnotationPresent(Class)
	 * @see Class#getDeclaredAnnotations()
	 * @deprecated as of 5.2 since it is superseded by the {@link MergedAnnotations} API
	 */
	@Deprecated
	@Nullable
	public static Class<?> findAnnotationDeclaringClass(
			Class<? extends Annotation> annotationType, @Nullable Class<?> clazz) {

		if (clazz == null) {
			return null;
		}

		return (Class<?>) MergedAnnotations.from(clazz, SearchStrategy.SUPERCLASS)
				.get(annotationType, MergedAnnotation::isDirectlyPresent)
				.getSource();
	}

	/**
	 * Find the first {@link Class} in the inheritance hierarchy of the
	 * specified {@code clazz} (including the specified {@code clazz} itself)
	 * on which at least one of the specified {@code annotationTypes} is
	 * <em>directly present</em>.
	 * <p>If the supplied {@code clazz} is an interface, only the interface
	 * itself will be checked; the inheritance hierarchy for interfaces will
	 * not be traversed.
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * <p>The standard {@link Class} API does not provide a mechanism for
	 * determining which class in an inheritance hierarchy actually declares
	 * one of several candidate {@linkplain Annotation annotations}, so we
	 * need to handle this explicitly.
	 * @param annotationTypes the annotation types to look for
	 * @param clazz the class to check for the annotation on (may be {@code null})
	 * @return the first {@link Class} in the inheritance hierarchy that
	 * declares an annotation of at least one of the specified
	 * {@code annotationTypes}, or {@code null} if not found
	 * @since 3.2.2
	 * @see Class#isAnnotationPresent(Class)
	 * @see Class#getDeclaredAnnotations()
	 * @deprecated as of 5.2 since it is superseded by the {@link MergedAnnotations} API
	 */
	@Deprecated
	@Nullable
	public static Class<?> findAnnotationDeclaringClassForTypes(
			List<Class<? extends Annotation>> annotationTypes, @Nullable Class<?> clazz) {

		if (clazz == null) {
			return null;
		}

		return (Class<?>) MergedAnnotations.from(clazz, SearchStrategy.SUPERCLASS)
				.stream()
				.filter(MergedAnnotationPredicates.typeIn(annotationTypes).and(MergedAnnotation::isDirectlyPresent))
				.map(MergedAnnotation::getSource)
				.findFirst().orElse(null);
	}

	/**
	 * Determine whether an annotation of the specified {@code annotationType}
	 * is declared locally (i.e. <em>directly present</em>) on the supplied
	 * {@code clazz}.
	 * <p>The supplied {@link Class} may represent any type.
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * <p>Note: This method does <strong>not</strong> determine if the annotation
	 * is {@linkplain java.lang.annotation.Inherited inherited}.
	 * @param annotationType the annotation type to look for
	 * @param clazz the class to check for the annotation on
	 * @return {@code true} if an annotation of the specified {@code annotationType}
	 * is <em>directly present</em>
	 * @see java.lang.Class#getDeclaredAnnotations()
	 * @see java.lang.Class#getDeclaredAnnotation(Class)
	 */
	public static boolean isAnnotationDeclaredLocally(Class<? extends Annotation> annotationType, Class<?> clazz) {
		return MergedAnnotations.from(clazz).get(annotationType).isDirectlyPresent();
	}

	/**
	 * Determine whether an annotation of the specified {@code annotationType}
	 * is <em>present</em> on the supplied {@code clazz} and is
	 * {@linkplain java.lang.annotation.Inherited inherited}
	 * (i.e. not <em>directly present</em>).
	 * <p>Meta-annotations will <em>not</em> be searched.
	 * <p>If the supplied {@code clazz} is an interface, only the interface
	 * itself will be checked. In accordance with standard meta-annotation
	 * semantics in Java, the inheritance hierarchy for interfaces will not
	 * be traversed. See the {@linkplain java.lang.annotation.Inherited javadoc}
	 * for the {@code @Inherited} meta-annotation for further details regarding
	 * annotation inheritance.
	 * @param annotationType the annotation type to look for
	 * @param clazz the class to check for the annotation on
	 * @return {@code true} if an annotation of the specified {@code annotationType}
	 * is <em>present</em> and <em>inherited</em>
	 * @see Class#isAnnotationPresent(Class)
	 * @see #isAnnotationDeclaredLocally(Class, Class)
	 * @deprecated as of 5.2 since it is superseded by the {@link MergedAnnotations} API
	 */
	@Deprecated
	public static boolean isAnnotationInherited(Class<? extends Annotation> annotationType, Class<?> clazz) {
		return MergedAnnotations.from(clazz, SearchStrategy.INHERITED_ANNOTATIONS)
				.stream(annotationType)
				.filter(MergedAnnotation::isDirectlyPresent)
				.findFirst().orElseGet(MergedAnnotation::missing)
				.getAggregateIndex() > 0;
	}

	/**
	 * Determine if an annotation of type {@code metaAnnotationType} is
	 * <em>meta-present</em> on the supplied {@code annotationType}.
	 * @param annotationType the annotation type to search on
	 * @param metaAnnotationType the type of meta-annotation to search for
	 * @return {@code true} if such an annotation is meta-present
	 * @since 4.2.1
	 * @deprecated as of 5.2 since it is superseded by the {@link MergedAnnotations} API
	 */
	@Deprecated
	public static boolean isAnnotationMetaPresent(Class<? extends Annotation> annotationType,
			@Nullable Class<? extends Annotation> metaAnnotationType) {

		if (metaAnnotationType == null) {
			return false;
		}
		// Shortcut: directly present on the element, with no merging needed?
		if (AnnotationFilter.PLAIN.matches(metaAnnotationType) ||
				AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotationType)) {
			return annotationType.isAnnotationPresent(metaAnnotationType);
		}
		// Exhaustive retrieval of merged annotations...
		return MergedAnnotations.from(annotationType, SearchStrategy.INHERITED_ANNOTATIONS,
				RepeatableContainers.none()).isPresent(metaAnnotationType);
	}

	/**
	 * Determine if the supplied {@link Annotation} is defined in the core JDK
	 * {@code java.lang.annotation} package.
	 * @param annotation the annotation to check
	 * @return {@code true} if the annotation is in the {@code java.lang.annotation} package
	 */
	public static boolean isInJavaLangAnnotationPackage(@Nullable Annotation annotation) {
		return (annotation != null && JAVA_LANG_ANNOTATION_FILTER.matches(annotation));
	}

	/**
	 * Determine if the {@link Annotation} with the supplied name is defined
	 * in the core JDK {@code java.lang.annotation} package.
	 * @param annotationType the name of the annotation type to check
	 * @return {@code true} if the annotation is in the {@code java.lang.annotation} package
	 * @since 4.2
	 */
	public static boolean isInJavaLangAnnotationPackage(@Nullable String annotationType) {
		return (annotationType != null && JAVA_LANG_ANNOTATION_FILTER.matches(annotationType));
	}

	/**
	 * Check the declared attributes of the given annotation, in particular covering
	 * Google App Engine's late arrival of {@code TypeNotPresentExceptionProxy} for
	 * {@code Class} values (instead of early {@code Class.getAnnotations() failure}.
	 * <p>This method not failing indicates that {@link #getAnnotationAttributes(Annotation)}
	 * won't failure either (when attempted later on).
	 * @param annotation the annotation to validate
	 * @throws IllegalStateException if a declared {@code Class} attribute could not be read
	 * @since 4.3.15
	 * @see Class#getAnnotations()
	 * @see #getAnnotationAttributes(Annotation)
	 */
	public static void validateAnnotation(Annotation annotation) {
		AttributeMethods.forAnnotationType(annotation.annotationType()).validate(annotation);
	}

	/**
	 * Retrieve the given annotation's attributes as a {@link Map}, preserving all
	 * attribute types.
	 * <p>Equivalent to calling {@link #getAnnotationAttributes(Annotation, boolean, boolean)}
	 * with the {@code classValuesAsString} and {@code nestedAnnotationsAsMap} parameters
	 * set to {@code false}.
	 * <p>Note: This method actually returns an {@link AnnotationAttributes} instance.
	 * However, the {@code Map} signature has been preserved for binary compatibility.
	 * @param annotation the annotation to retrieve the attributes for
	 * @return the Map of annotation attributes, with attribute names as keys and
	 * corresponding attribute values as values (never {@code null})
	 * @see #getAnnotationAttributes(AnnotatedElement, Annotation)
	 * @see #getAnnotationAttributes(Annotation, boolean, boolean)
	 * @see #getAnnotationAttributes(AnnotatedElement, Annotation, boolean, boolean)
	 */
	public static Map<String, Object> getAnnotationAttributes(Annotation annotation) {
		return getAnnotationAttributes(null, annotation);
	}

	/**
	 * Retrieve the given annotation's attributes as a {@link Map}.
	 * <p>Equivalent to calling {@link #getAnnotationAttributes(Annotation, boolean, boolean)}
	 * with the {@code nestedAnnotationsAsMap} parameter set to {@code false}.
	 * <p>Note: This method actually returns an {@link AnnotationAttributes} instance.
	 * However, the {@code Map} signature has been preserved for binary compatibility.
	 * @param annotation the annotation to retrieve the attributes for
	 * @param classValuesAsString whether to convert Class references into Strings (for
	 * compatibility with {@link org.springframework.core.type.AnnotationMetadata})
	 * or to preserve them as Class references
	 * @return the Map of annotation attributes, with attribute names as keys and
	 * corresponding attribute values as values (never {@code null})
	 * @see #getAnnotationAttributes(Annotation, boolean, boolean)
	 */
	public static Map<String, Object> getAnnotationAttributes(
			Annotation annotation, boolean classValuesAsString) {

		return getAnnotationAttributes(annotation, classValuesAsString, false);
	}

	/**
	 * Retrieve the given annotation's attributes as an {@link AnnotationAttributes} map.
	 * <p>This method provides fully recursive annotation reading capabilities on par with
	 * the reflection-based {@link org.springframework.core.type.StandardAnnotationMetadata}.
	 * @param annotation the annotation to retrieve the attributes for
	 * @param classValuesAsString whether to convert Class references into Strings (for
	 * compatibility with {@link org.springframework.core.type.AnnotationMetadata})
	 * or to preserve them as Class references
	 * @param nestedAnnotationsAsMap whether to convert nested annotations into
	 * {@link AnnotationAttributes} maps (for compatibility with
	 * {@link org.springframework.core.type.AnnotationMetadata}) or to preserve them as
	 * {@code Annotation} instances
	 * @return the annotation attributes (a specialized Map) with attribute names as keys
	 * and corresponding attribute values as values (never {@code null})
	 * @since 3.1.1
	 */
	public static AnnotationAttributes getAnnotationAttributes(
			Annotation annotation, boolean classValuesAsString, boolean nestedAnnotationsAsMap) {

		return getAnnotationAttributes(null, annotation, classValuesAsString, nestedAnnotationsAsMap);
	}

	/**
	 * Retrieve the given annotation's attributes as an {@link AnnotationAttributes} map.
	 * <p>Equivalent to calling {@link #getAnnotationAttributes(AnnotatedElement, Annotation, boolean, boolean)}
	 * with the {@code classValuesAsString} and {@code nestedAnnotationsAsMap} parameters
	 * set to {@code false}.
	 * @param annotatedElement the element that is annotated with the supplied annotation;
	 * may be {@code null} if unknown
	 * @param annotation the annotation to retrieve the attributes for
	 * @return the annotation attributes (a specialized Map) with attribute names as keys
	 * and corresponding attribute values as values (never {@code null})
	 * @since 4.2
	 * @see #getAnnotationAttributes(AnnotatedElement, Annotation, boolean, boolean)
	 */
	public static AnnotationAttributes getAnnotationAttributes(
			@Nullable AnnotatedElement annotatedElement, Annotation annotation) {

		return getAnnotationAttributes(annotatedElement, annotation, false, false);
	}

	/**
	 * Retrieve the given annotation's attributes as an {@link AnnotationAttributes} map.
	 * <p>This method provides fully recursive annotation reading capabilities on par with
	 * the reflection-based {@link org.springframework.core.type.StandardAnnotationMetadata}.
	 * @param annotatedElement the element that is annotated with the supplied annotation;
	 * may be {@code null} if unknown
	 * @param annotation the annotation to retrieve the attributes for
	 * @param classValuesAsString whether to convert Class references into Strings (for
	 * compatibility with {@link org.springframework.core.type.AnnotationMetadata})
	 * or to preserve them as Class references
	 * @param nestedAnnotationsAsMap whether to convert nested annotations into
	 * {@link AnnotationAttributes} maps (for compatibility with
	 * {@link org.springframework.core.type.AnnotationMetadata}) or to preserve them as
	 * {@code Annotation} instances
	 * @return the annotation attributes (a specialized Map) with attribute names as keys
	 * and corresponding attribute values as values (never {@code null})
	 * @since 4.2
	 */
	public static AnnotationAttributes getAnnotationAttributes(
			@Nullable AnnotatedElement annotatedElement, Annotation annotation,
			boolean classValuesAsString, boolean nestedAnnotationsAsMap) {

		Adapt[] adaptations = Adapt.values(classValuesAsString, nestedAnnotationsAsMap);
		return MergedAnnotation.from(annotatedElement, annotation)
				.withNonMergedAttributes()
				.asMap(mergedAnnotation ->
						new AnnotationAttributes(mergedAnnotation.getType(), true), adaptations);
	}

	/**
	 * Register the annotation-declared default values for the given attributes,
	 * if available.
	 * @param attributes the annotation attributes to process
	 * @since 4.3.2
	 */
	public static void registerDefaultValues(AnnotationAttributes attributes) {
		Class<? extends Annotation> annotationType = attributes.annotationType();
		if (annotationType != null && Modifier.isPublic(annotationType.getModifiers()) &&
				!AnnotationFilter.PLAIN.matches(annotationType)) {
			Map<String, DefaultValueHolder> defaultValues = getDefaultValues(annotationType);
			defaultValues.forEach(attributes::putIfAbsent);
		}
	}

	private static Map<String, DefaultValueHolder> getDefaultValues(
			Class<? extends Annotation> annotationType) {

		return defaultValuesCache.computeIfAbsent(annotationType,
				AnnotationUtils::computeDefaultValues);
	}

	private static Map<String, DefaultValueHolder> computeDefaultValues(
			Class<? extends Annotation> annotationType) {

		AttributeMethods methods = AttributeMethods.forAnnotationType(annotationType);
		if (!methods.hasDefaultValueMethod()) {
			return Collections.emptyMap();
		}
		Map<String, DefaultValueHolder> result = CollectionUtils.newLinkedHashMap(methods.size());
		if (!methods.hasNestedAnnotation()) {
			// Use simpler method if there are no nested annotations
			for (int i = 0; i < methods.size(); i++) {
				Method method = methods.get(i);
				Object defaultValue = method.getDefaultValue();
				if (defaultValue != null) {
					result.put(method.getName(), new DefaultValueHolder(defaultValue));
				}
			}
		}
		else {
			// If we have nested annotations, we need them as nested maps
			AnnotationAttributes attributes = MergedAnnotation.of(annotationType)
					.asMap(annotation ->
							new AnnotationAttributes(annotation.getType(), true), Adapt.ANNOTATION_TO_MAP);
			for (Map.Entry<String, Object> element : attributes.entrySet()) {
				result.put(element.getKey(), new DefaultValueHolder(element.getValue()));
			}
		}
		return result;
	}

	/**
	 * Post-process the supplied {@link AnnotationAttributes}, preserving nested
	 * annotations as {@code Annotation} instances.
	 * <p>Specifically, this method enforces <em>attribute alias</em> semantics
	 * for annotation attributes that are annotated with {@link AliasFor @AliasFor}
	 * and replaces default value placeholders with their original default values.
	 * @param annotatedElement the element that is annotated with an annotation or
	 * annotation hierarchy from which the supplied attributes were created;
	 * may be {@code null} if unknown
	 * @param attributes the annotation attributes to post-process
	 * @param classValuesAsString whether to convert Class references into Strings (for
	 * compatibility with {@link org.springframework.core.type.AnnotationMetadata})
	 * or to preserve them as Class references
	 * @since 4.3.2
	 * @see #getDefaultValue(Class, String)
	 */
	public static void postProcessAnnotationAttributes(@Nullable Object annotatedElement,
			@Nullable AnnotationAttributes attributes, boolean classValuesAsString) {

		if (attributes == null) {
			return;
		}
		if (!attributes.validated) {
			Class<? extends Annotation> annotationType = attributes.annotationType();
			if (annotationType == null) {
				return;
			}
			AnnotationTypeMapping mapping = AnnotationTypeMappings.forAnnotationType(annotationType).get(0);
			for (int i = 0; i < mapping.getMirrorSets().size(); i++) {
				MirrorSet mirrorSet = mapping.getMirrorSets().get(i);
				int resolved = mirrorSet.resolve(attributes.displayName, attributes,
						AnnotationUtils::getAttributeValueForMirrorResolution);
				if (resolved != -1) {
					Method attribute = mapping.getAttributes().get(resolved);
					Object value = attributes.get(attribute.getName());
					for (int j = 0; j < mirrorSet.size(); j++) {
						Method mirror = mirrorSet.get(j);
						if (mirror != attribute) {
							attributes.put(mirror.getName(),
									adaptValue(annotatedElement, value, classValuesAsString));
						}
					}
				}
			}
		}
		for (Map.Entry<String, Object> attributeEntry : attributes.entrySet()) {
			String attributeName = attributeEntry.getKey();
			Object value = attributeEntry.getValue();
			if (value instanceof DefaultValueHolder) {
				value = ((DefaultValueHolder) value).defaultValue;
				attributes.put(attributeName,
						adaptValue(annotatedElement, value, classValuesAsString));
			}
		}
	}

	private static Object getAttributeValueForMirrorResolution(Method attribute, Object attributes) {
		Object result = ((AnnotationAttributes) attributes).get(attribute.getName());
		return (result instanceof DefaultValueHolder ? ((DefaultValueHolder) result).defaultValue : result);
	}

	@Nullable
	private static Object adaptValue(
			@Nullable Object annotatedElement, @Nullable Object value, boolean classValuesAsString) {

		if (classValuesAsString) {
			if (value instanceof Class) {
				return ((Class<?>) value).getName();
			}
			if (value instanceof Class[]) {
				Class<?>[] classes = (Class<?>[]) value;
				String[] names = new String[classes.length];
				for (int i = 0; i < classes.length; i++) {
					names[i] = classes[i].getName();
				}
				return names;
			}
		}
		if (value instanceof Annotation) {
			Annotation annotation = (Annotation) value;
			return MergedAnnotation.from(annotatedElement, annotation).synthesize();
		}
		if (value instanceof Annotation[]) {
			Annotation[] annotations = (Annotation[]) value;
			Annotation[] synthesized = (Annotation[]) Array.newInstance(
					annotations.getClass().getComponentType(), annotations.length);
			for (int i = 0; i < annotations.length; i++) {
				synthesized[i] = MergedAnnotation.from(annotatedElement, annotations[i]).synthesize();
			}
			return synthesized;
		}
		return value;
	}

	/**
	 * Retrieve the <em>value</em> of the {@code value} attribute of a
	 * single-element Annotation, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the value
	 * @return the attribute value, or {@code null} if not found unless the attribute
	 * value cannot be retrieved due to an {@link AnnotationConfigurationException},
	 * in which case such an exception will be rethrown
	 * @see #getValue(Annotation, String)
	 */
	@Nullable
	public static Object getValue(Annotation annotation) {
		return getValue(annotation, VALUE);
	}

	/**
	 * Retrieve the <em>value</em> of a named attribute, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the value
	 * @param attributeName the name of the attribute value to retrieve
	 * @return the attribute value, or {@code null} if not found unless the attribute
	 * value cannot be retrieved due to an {@link AnnotationConfigurationException},
	 * in which case such an exception will be rethrown
	 * @see #getValue(Annotation)
	 */
	@Nullable
	public static Object getValue(@Nullable Annotation annotation, @Nullable String attributeName) {
		if (annotation == null || !StringUtils.hasText(attributeName)) {
			return null;
		}
		try {
			Method method = annotation.annotationType().getDeclaredMethod(attributeName);
			ReflectionUtils.makeAccessible(method);
			return method.invoke(annotation);
		}
		catch (NoSuchMethodException ex) {
			return null;
		}
		catch (InvocationTargetException ex) {
			rethrowAnnotationConfigurationException(ex.getTargetException());
			throw new IllegalStateException("Could not obtain value for annotation attribute '" +
					attributeName + "' in " + annotation, ex);
		}
		catch (Throwable ex) {
			handleIntrospectionFailure(annotation.getClass(), ex);
			return null;
		}
	}

	/**
	 * If the supplied throwable is an {@link AnnotationConfigurationException},
	 * it will be cast to an {@code AnnotationConfigurationException} and thrown,
	 * allowing it to propagate to the caller.
	 * <p>Otherwise, this method does nothing.
	 * @param ex the throwable to inspect
	 */
	static void rethrowAnnotationConfigurationException(Throwable ex) {
		if (ex instanceof AnnotationConfigurationException) {
			throw (AnnotationConfigurationException) ex;
		}
	}

	/**
	 * Handle the supplied annotation introspection exception.
	 * <p>If the supplied exception is an {@link AnnotationConfigurationException},
	 * it will simply be thrown, allowing it to propagate to the caller, and
	 * nothing will be logged.
	 * <p>Otherwise, this method logs an introspection failure (in particular for
	 * a {@link TypeNotPresentException}) before moving on, assuming nested
	 * {@code Class} values were not resolvable within annotation attributes and
	 * thereby effectively pretending there were no annotations on the specified
	 * element.
	 * @param element the element that we tried to introspect annotations on
	 * @param ex the exception that we encountered
	 * @see #rethrowAnnotationConfigurationException
	 * @see IntrospectionFailureLogger
	 */
	static void handleIntrospectionFailure(@Nullable AnnotatedElement element, Throwable ex) {
		rethrowAnnotationConfigurationException(ex);
		IntrospectionFailureLogger logger = IntrospectionFailureLogger.INFO;
		boolean meta = false;
		if (element instanceof Class && Annotation.class.isAssignableFrom((Class<?>) element)) {
			// Meta-annotation or (default) value lookup on an annotation type
			logger = IntrospectionFailureLogger.DEBUG;
			meta = true;
		}
		if (logger.isEnabled()) {
			String message = meta ?
					"Failed to meta-introspect annotation " :
					"Failed to introspect annotations on ";
			logger.log(message + element + ": " + ex);
		}
	}

	/**
	 * Retrieve the <em>default value</em> of the {@code value} attribute
	 * of a single-element Annotation, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the default value
	 * @return the default value, or {@code null} if not found
	 * @see #getDefaultValue(Annotation, String)
	 */
	@Nullable
	public static Object getDefaultValue(Annotation annotation) {
		return getDefaultValue(annotation, VALUE);
	}

	/**
	 * Retrieve the <em>default value</em> of a named attribute, given an annotation instance.
	 * @param annotation the annotation instance from which to retrieve the default value
	 * @param attributeName the name of the attribute value to retrieve
	 * @return the default value of the named attribute, or {@code null} if not found
	 * @see #getDefaultValue(Class, String)
	 */
	@Nullable
	public static Object getDefaultValue(@Nullable Annotation annotation, @Nullable String attributeName) {
		return (annotation != null ? getDefaultValue(annotation.annotationType(), attributeName) : null);
	}

	/**
	 * Retrieve the <em>default value</em> of the {@code value} attribute
	 * of a single-element Annotation, given the {@link Class annotation type}.
	 * @param annotationType the <em>annotation type</em> for which the default value should be retrieved
	 * @return the default value, or {@code null} if not found
	 * @see #getDefaultValue(Class, String)
	 */
	@Nullable
	public static Object getDefaultValue(Class<? extends Annotation> annotationType) {
		return getDefaultValue(annotationType, VALUE);
	}

	/**
	 * Retrieve the <em>default value</em> of a named attribute, given the
	 * {@link Class annotation type}.
	 * @param annotationType the <em>annotation type</em> for which the default value should be retrieved
	 * @param attributeName the name of the attribute value to retrieve.
	 * @return the default value of the named attribute, or {@code null} if not found
	 * @see #getDefaultValue(Annotation, String)
	 */
	@Nullable
	public static Object getDefaultValue(
			@Nullable Class<? extends Annotation> annotationType, @Nullable String attributeName) {

		if (annotationType == null || !StringUtils.hasText(attributeName)) {
			return null;
		}
		return MergedAnnotation.of(annotationType).getDefaultValue(attributeName).orElse(null);
	}

	/**
	 * <em>Synthesize</em> an annotation from the supplied {@code annotation}
	 * by wrapping it in a dynamic proxy that transparently enforces
	 * <em>attribute alias</em> semantics for annotation attributes that are
	 * annotated with {@link AliasFor @AliasFor}.
	 * @param annotation the annotation to synthesize
	 * @param annotatedElement the element that is annotated with the supplied
	 * annotation; may be {@code null} if unknown
	 * @return the synthesized annotation if the supplied annotation is
	 * <em>synthesizable</em>; {@code null} if the supplied annotation is
	 * {@code null}; otherwise the supplied annotation unmodified
	 * @throws AnnotationConfigurationException if invalid configuration of
	 * {@code @AliasFor} is detected
	 * @since 4.2
	 * @see #synthesizeAnnotation(Map, Class, AnnotatedElement)
	 * @see #synthesizeAnnotation(Class)
	 */
	public static <A extends Annotation> A synthesizeAnnotation(
			A annotation, @Nullable AnnotatedElement annotatedElement) {

		if (annotation instanceof SynthesizedAnnotation || AnnotationFilter.PLAIN.matches(annotation)) {
			return annotation;
		}
		return MergedAnnotation.from(annotatedElement, annotation).synthesize();
	}

	/**
	 * <em>Synthesize</em> an annotation from its default attributes values.
	 * <p>This method simply delegates to
	 * {@link #synthesizeAnnotation(Map, Class, AnnotatedElement)},
	 * supplying an empty map for the source attribute values and {@code null}
	 * for the {@link AnnotatedElement}.
	 * @param annotationType the type of annotation to synthesize
	 * @return the synthesized annotation
	 * @throws IllegalArgumentException if a required attribute is missing
	 * @throws AnnotationConfigurationException if invalid configuration of
	 * {@code @AliasFor} is detected
	 * @since 4.2
	 * @see #synthesizeAnnotation(Map, Class, AnnotatedElement)
	 * @see #synthesizeAnnotation(Annotation, AnnotatedElement)
	 */
	public static <A extends Annotation> A synthesizeAnnotation(Class<A> annotationType) {
		return synthesizeAnnotation(Collections.emptyMap(), annotationType, null);
	}

	/**
	 * <em>Synthesize</em> an annotation from the supplied map of annotation
	 * attributes by wrapping the map in a dynamic proxy that implements an
	 * annotation of the specified {@code annotationType} and transparently
	 * enforces <em>attribute alias</em> semantics for annotation attributes
	 * that are annotated with {@link AliasFor @AliasFor}.
	 * <p>The supplied map must contain a key-value pair for every attribute
	 * defined in the supplied {@code annotationType} that is not aliased or
	 * does not have a default value. Nested maps and nested arrays of maps
	 * will be recursively synthesized into nested annotations or nested
	 * arrays of annotations, respectively.
	 * <p>Note that {@link AnnotationAttributes} is a specialized type of
	 * {@link Map} that is an ideal candidate for this method's
	 * {@code attributes} argument.
	 * @param attributes the map of annotation attributes to synthesize
	 * @param annotationType the type of annotation to synthesize
	 * @param annotatedElement the element that is annotated with the annotation
	 * corresponding to the supplied attributes; may be {@code null} if unknown
	 * @return the synthesized annotation
	 * @throws IllegalArgumentException if a required attribute is missing or if an
	 * attribute is not of the correct type
	 * @throws AnnotationConfigurationException if invalid configuration of
	 * {@code @AliasFor} is detected
	 * @since 4.2
	 * @see #synthesizeAnnotation(Annotation, AnnotatedElement)
	 * @see #synthesizeAnnotation(Class)
	 * @see #getAnnotationAttributes(AnnotatedElement, Annotation)
	 * @see #getAnnotationAttributes(AnnotatedElement, Annotation, boolean, boolean)
	 */
	public static <A extends Annotation> A synthesizeAnnotation(Map<String, Object> attributes,
			Class<A> annotationType, @Nullable AnnotatedElement annotatedElement) {

		try {
			return MergedAnnotation.of(annotatedElement, annotationType, attributes).synthesize();
		}
		catch (NoSuchElementException | IllegalStateException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	/**
	 * <em>Synthesize</em> an array of annotations from the supplied array
	 * of {@code annotations} by creating a new array of the same size and
	 * type and populating it with {@linkplain #synthesizeAnnotation(Annotation,
	 * AnnotatedElement) synthesized} versions of the annotations from the input
	 * array.
	 * @param annotations the array of annotations to synthesize
	 * @param annotatedElement the element that is annotated with the supplied
	 * array of annotations; may be {@code null} if unknown
	 * @return a new array of synthesized annotations, or {@code null} if
	 * the supplied array is {@code null}
	 * @throws AnnotationConfigurationException if invalid configuration of
	 * {@code @AliasFor} is detected
	 * @since 4.2
	 * @see #synthesizeAnnotation(Annotation, AnnotatedElement)
	 * @see #synthesizeAnnotation(Map, Class, AnnotatedElement)
	 */
	static Annotation[] synthesizeAnnotationArray(Annotation[] annotations, AnnotatedElement annotatedElement) {
		if (AnnotationsScanner.hasPlainJavaAnnotationsOnly(annotatedElement)) {
			return annotations;
		}
		Annotation[] synthesized = (Annotation[]) Array.newInstance(
				annotations.getClass().getComponentType(), annotations.length);
		for (int i = 0; i < annotations.length; i++) {
			synthesized[i] = synthesizeAnnotation(annotations[i], annotatedElement);
		}
		return synthesized;
	}

	/**
	 * Clear the internal annotation metadata cache.
	 * @since 4.3.15
	 */
	public static void clearCache() {
		AnnotationTypeMappings.clearCache();
		AnnotationsScanner.clearCache();
	}


	/**
	 * Internal holder used to wrap default values.
	 */
	private static class DefaultValueHolder {

		final Object defaultValue;

		public DefaultValueHolder(Object defaultValue) {
			this.defaultValue = defaultValue;
		}

		@Override
		public String toString() {
			return "*" + this.defaultValue;
		}
	}

}
