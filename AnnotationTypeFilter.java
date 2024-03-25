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

package org.springframework.core.type.filter;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * A simple {@link TypeFilter} which matches classes with a given annotation,
 * checking inherited annotations as well.
 *
 * <p>By default, the matching logic mirrors that of
 * {@link AnnotationUtils#getAnnotation(java.lang.reflect.AnnotatedElement, Class)},
 * supporting annotations that are <em>present</em> or <em>meta-present</em> for a
 * single level of meta-annotations. The search for meta-annotations my be disabled.
 * Similarly, the search for annotations on interfaces may optionally be enabled.
 * Consult the various constructors in this class for details.
 *
 * @author Mark Fisher
 * @author Ramnivas Laddad
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 2.5
 */
public class AnnotationTypeFilter extends AbstractTypeHierarchyTraversingFilter {

	// 注解的Class对象
	private final Class<? extends Annotation> annotationType;

	// 是否考虑元注解
	private final boolean considerMetaAnnotations;


	/**
		Create a new AnnotationTypeFilter for the given annotation type.

		The filter will also match meta-annotations. 
		To disable the meta-annotation matching, use the constructor that accepts a 'considerMetaAnnotations' argument.
		The filter will not match interfaces.
		
		Params:
		 annotationType – the annotation type to match
	 */
    // 构造方法1
	public AnnotationTypeFilter(Class<? extends Annotation> annotationType) {               // 注解的Class对象
		                this(annotationType, 
     true,
          false);
	}


	

	/**
		Create a new AnnotationTypeFilter for the given annotation type.
		Params:
		annotationType – the annotation type to match considerMetaAnnotations – whether to also match on meta-annotations considerInterfaces – whether to also match interfaces
	 */
    // 构造方法3
	public AnnotationTypeFilter(Class<? extends Annotation> annotationType, 
								boolean considerMetaAnnotations,    // true
								boolean considerInterfaces) {       // false 

		super(annotationType.isAnnotationPresent(Inherited.class), considerInterfaces);
		this.annotationType = annotationType;
		this.considerMetaAnnotations = considerMetaAnnotations;
	}



 

	@Override
	protected boolean matchSelf(MetadataReader metadataReader) {
		AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();
		return metadata.hasAnnotation(this.annotationType.getName()) ||
				(this.considerMetaAnnotations && metadata.hasMetaAnnotation(this.annotationType.getName()));
	}


	

 

}
