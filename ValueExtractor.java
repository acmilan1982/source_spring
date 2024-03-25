 
package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
    Strategy API for extracting a value for an annotation attribute from a given source object which is typically an Annotation, Map, or TypeMappedAnnotation.
    Since:
    5.2.4
    Author:
    Sam Brannen
 */
@FunctionalInterface
interface ValueExtractor {

	/**
	 *  Extract the annotation attribute represented by the supplied Method from the supplied source
	 */
	@Nullable
	Object extract(Method attribute, @Nullable Object object);

}
