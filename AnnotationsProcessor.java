
package org.springframework.core.annotation;

import java.lang.annotation.Annotation;

import org.springframework.lang.Nullable;

/**
		Callback interface used to process annotations.

		Since:
		  5.2
		See Also:
		  AnnotationsScanner, TypeMappedAnnotations
		Author:
		  Phillip Webb
		Type parameters:
		   <C> – the context type 
		   <R> – the result type
 */
@FunctionalInterface
interface AnnotationsProcessor<C, R> {

	/**
		Called when an aggregate is about to be processed. This method may return a non-null result to short-circuit any further processing.
		Params:
		context – the context information relevant to the processor aggregateIndex – the aggregate index about to be processed
		Returns:
		a non-null result if no further processing is required
	 */
	@Nullable
	default R doWithAggregate(C context, int aggregateIndex) {
		return null;
	}

	/**
		Called when an array of annotations can be processed. 
		This method may return a non-null result to short-circuit any further processing.
		Params:
		  context – the context information relevant to the processor 
		  aggregateIndex – the aggregate index of the provided annotations 
		  source – the original source of the annotations, if known 
		  annotations – the annotations to process (this array may contain null elements)
		Returns:
		a non-null result if no further processing is required
	 */
	@Nullable
	R doWithAnnotations(C context, int aggregateIndex, @Nullable Object source, Annotation[] annotations);

	/**
Get the final result to be returned. By default this method returns the last process result.
Params:
result – the last early exit result, or null if none
Returns:
the final result to be returned to the caller
	 */
	@Nullable
	default R finish(@Nullable R result) {
		return result;
	}

}
