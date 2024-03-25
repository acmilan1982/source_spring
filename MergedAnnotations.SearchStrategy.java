 

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

 
public interface MergedAnnotations extends Iterable<MergedAnnotation<Annotation>> {

 

 

	/**
	 * Search strategies supported by
	 * {@link MergedAnnotations#from(AnnotatedElement, SearchStrategy)} and
	 * variants of that method.
	 *
	 * <p>Each strategy creates a different set of aggregates that will be
	 * combined to create the final {@link MergedAnnotations}.
	 */
	enum SearchStrategy {

		/**
		 * Find only directly declared annotations, without considering
		 * {@link Inherited @Inherited} annotations and without searching
		 * superclasses or implemented interfaces.
		 */
		DIRECT,

		/**
		 * Find all directly declared annotations as well as any
		 * {@link Inherited @Inherited} superclass annotations.
		 * <p>This strategy is only really useful when used with {@link Class}
		 * types since the {@link Inherited @Inherited} annotation is ignored for
		 * all other {@linkplain AnnotatedElement annotated elements}.
		 * <p>This strategy does not search implemented interfaces.
		 */
		INHERITED_ANNOTATIONS,

		/**
		 * Find all directly declared and superclass annotations.
		 * <p>This strategy is similar to {@link #INHERITED_ANNOTATIONS} except
		 * the annotations do not need to be meta-annotated with
		 * {@link Inherited @Inherited}.
		 * <p>This strategy does not search implemented interfaces.
		 */
		SUPERCLASS,

		/**
		 * Perform a full search of the entire type hierarchy, including
		 * superclasses and implemented interfaces.
		 * <p>Superclass annotations do not need to be meta-annotated with
		 * {@link Inherited @Inherited}.
		 */
		TYPE_HIERARCHY,

		/**
		 * Perform a full search of the entire type hierarchy on the source
		 * <em>and</em> any enclosing classes.
		 * <p>This strategy is similar to {@link #TYPE_HIERARCHY} except that
		 * {@linkplain Class#getEnclosingClass() enclosing classes} are also
		 * searched.
		 * <p>Superclass and enclosing class annotations do not need to be
		 * meta-annotated with {@link Inherited @Inherited}.
		 * <p>When searching a {@link Method} source, this strategy is identical
		 * to {@link #TYPE_HIERARCHY}.
		 * <p><strong>WARNING:</strong> This strategy searches recursively for
		 * annotations on the enclosing class for any source type, regardless
		 * whether the source type is an <em>inner class</em>, a {@code static}
		 * nested class, or a nested interface. Thus, it may find more annotations
		 * than you would expect.
		 */
		TYPE_HIERARCHY_AND_ENCLOSING_CLASSES

	}

}
