/*
 * Copyright 2002-2022 the original author or authors.
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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;

/**
 * Provides a quick way to access the attribute methods of an {@link Annotation}
 * with consistent ordering as well as a few useful utility methods.
 *
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 5.2
 */
final class AttributeMethods {

	static final AttributeMethods NONE = new AttributeMethods(null, new Method[0]);


	/*
	 *   缓存，key:   注解的Class对象 
	 *        value:  注解属性对应的所有属性Method
	 * 
	 */
	private static final Map<Class<? extends Annotation>, AttributeMethods> cache = new ConcurrentReferenceHashMap<>();
	

	private static final Comparator<Method> methodComparator = (m1, m2) -> {
		if (m1 != null && m2 != null) {
			return m1.getName().compareTo(m2.getName());
		}
		return m1 != null ? -1 : 1;
	};


    // 注解的Class对象 
	@Nullable
	private final Class<? extends Annotation> annotationType;


	// 注解对应的所有属性Method
	private final Method[] attributeMethods;

	private final boolean[] canThrowTypeNotPresentException;

	private final boolean hasDefaultValueMethod;

	private final boolean hasNestedAnnotation;


	private AttributeMethods(@Nullable Class<? extends Annotation> annotationType,   // Annotation的Class对象
	                         Method[] attributeMethods) {                            // AnnotationClass对象的所有属性方法
		this.annotationType = annotationType;
		this.attributeMethods = attributeMethods;
		this.canThrowTypeNotPresentException = new boolean[attributeMethods.length];
		boolean foundDefaultValueMethod = false;
		boolean foundNestedAnnotation = false;

		for (int i = 0; i < attributeMethods.length; i++) {
			Method method = this.attributeMethods[i];
			Class<?> type = method.getReturnType();
			if (!foundDefaultValueMethod && (method.getDefaultValue() != null)) {
				foundDefaultValueMethod = true;
			}
			if (!foundNestedAnnotation && (type.isAnnotation() || (type.isArray() && type.getComponentType().isAnnotation()))) {
				foundNestedAnnotation = true;
			}
			ReflectionUtils.makeAccessible(method);
			this.canThrowTypeNotPresentException[i] = (type == Class.class || type == Class[].class || type.isEnum());
		}
		
		this.hasDefaultValueMethod = foundDefaultValueMethod;
		this.hasNestedAnnotation = foundNestedAnnotation;
	}

    /**
        Get the number of attributes in this collection.

        Returns:
          the number of attributes

        private final Method[] attributeMethods;
    */
    // 代码5
	int size() {
		return this.attributeMethods.length;
	}


    

	

 


	/**
	 * 
		Get the attribute methods for the given annotation type.
		Params:
		annotationType – the annotation type
		Returns:
		the attribute methods for the annotation type

		获取指定注解的所有的属性方法，并缓存
	 */
	// 代码10
	static AttributeMethods forAnnotationType(@Nullable Class<? extends Annotation> annotationType) {   // annotationType: 注解的Class对象
		if (annotationType == null) {
			return NONE;
		}

		// private static final Map<Class<? extends Annotation>, AttributeMethods> cache = new ConcurrentReferenceHashMap<>();
		return cache.computeIfAbsent(annotationType, AttributeMethods::compute);      // AttributeMethods::compute见代码11
	}


	/**
	 * 
	     为当前注解对象创建对应的AttributeMethods，其持有该注解的所有属性方法
	 */
	// 代码11
	private static AttributeMethods compute(Class<? extends Annotation> annotationType) {
		// 获取指定注解的所有的属性方法
		Method[] methods = annotationType.getDeclaredMethods();
		int size = methods.length;
		for (int i = 0; i < methods.length; i++) {
			// 判断某个方法，是否属性方法，即没有参数，但返回值不为 void
			if (!isAttributeMethod(methods[i])) {    // 见代码12
				methods[i] = null;
				size--;
			}
		}
		if (size == 0) {
			return NONE;
		}
		Arrays.sort(methods, methodComparator);
		Method[] attributeMethods = Arrays.copyOf(methods, size);
		// 为当前注解对象创建对应的AttributeMethods，其持有该注解的所有属性方法
		return new AttributeMethods(annotationType, attributeMethods);
	}

	// 判断某个方法，是否属性方法，即没有参数，但返回值不为 void
	// 代码12
	private static boolean isAttributeMethod(Method method) {
		return (method.getParameterCount() == 0 && method.getReturnType() != void.class);
	}


    /*
		Get the attribute with the specified name or null if no matching attribute exists.
		Params:
		  name – the attribute name to find
		Returns:
		  the attribute method or null
	 * 
	 */
	// 代码13
	@Nullable
	Method get(String name) {
		int index = indexOf(name);
		return index != -1 ? this.attributeMethods[index] : null;
	}

    /*
		Get the index of the attribute with the specified name, or -1 if there is no attribute with the name.
		Params:
		name – the name to find
		Returns:
		the index of the attribute, or -1
	 * 
	 */
	// 代码14
	int indexOf(String name) {
		for (int i = 0; i < this.attributeMethods.length; i++) {
			if (this.attributeMethods[i].getName().equals(name)) {
				return i;
			}
		}
		return -1;
	}
    /*
        Get the attribute at the specified index.

        Params:
          index – the index of the attribute to return
        Returns:
          the attribute method
        Throws:
          IndexOutOfBoundsException – if the index is out of range (index < 0 || index >= size())

        private final Method[] attributeMethods;
    */
    // 代码15
	Method get(int index) {
		return this.attributeMethods[index];
	}



	/**
	 * Create a description for the given attribute method suitable to use in
	 * exception messages and logs.
	 * @param attribute the attribute to describe
	 * @return a description of the attribute
	 */
	static String describe(@Nullable Method attribute) {
		if (attribute == null) {
			return "(none)";
		}
		return describe(attribute.getDeclaringClass(), attribute.getName());
	}

	/**
	 * Create a description for the given attribute method suitable to use in
	 * exception messages and logs.
	 * @param annotationType the annotation type
	 * @param attributeName the attribute name
	 * @return a description of the attribute
	 */
	static String describe(@Nullable Class<?> annotationType, @Nullable String attributeName) {
		if (attributeName == null) {
			return "(none)";
		}
		String in = (annotationType != null ? " in annotation [" + annotationType.getName() + "]" : "");
		return "attribute '" + attributeName + "'" + in;
	}

}
