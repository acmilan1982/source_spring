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
import java.lang.reflect.Array;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
	MergedAnnotation that adapts attributes from a root annotation by applying the mapping and mirroring rules of an AnnotationTypeMapping.

	Root attribute values are extracted from a source object using a supplied BiFunction. 
	This allows various different annotation models to be supported by the same class. 
	For example, the attributes source might be an actual Annotation instance where methods on the annotation instance are invoked to extract values.
	Equally, the source could be a simple Map with values extracted using Map.get(Object).

	根属性值是使用提供的BiFunction从源对象中提取的。这允许同一个类支持各种不同的注释模型。
	例如，属性源可能是一个实际的Annotation实例，其中会调用注释实例上的方法来提取值。同样，源可以是一个简单的Map，其中的值是使用Map.get（Object）提取的。

	Extracted root attribute values must be compatible with the attribute return type, namely:

	Return Type
	Extracted Type
	Class
	Class or String
	Class[]
	Class[] or String[]
	Annotation
	Annotation, Map, or Object compatible with the value extractor
	Annotation[]
	Annotation[], Map[], or Object[] where elements are compatible with the value extractor
	Other types
	An exact match or the appropriate primitive wrapper
	
	Since:
	5.2
	See Also:
	TypeMappedAnnotations
	Author:
	Phillip Webb, Juergen Hoeller, Sam Brannen
	Type parameters:
	<A> – the annotation type
 */
final class TypeMappedAnnotation<A extends Annotation> extends AbstractMergedAnnotation<A> {

	private static final Map<Class<?>, Object> EMPTY_ARRAYS;
	static {
		Map<Class<?>, Object> emptyArrays = new HashMap<>();
		emptyArrays.put(boolean.class, new boolean[0]);
		emptyArrays.put(byte.class, new byte[0]);
		emptyArrays.put(char.class, new char[0]);
		emptyArrays.put(double.class, new double[0]);
		emptyArrays.put(float.class, new float[0]);
		emptyArrays.put(int.class, new int[0]);
		emptyArrays.put(long.class, new long[0]);
		emptyArrays.put(short.class, new short[0]);
		emptyArrays.put(String.class, new String[0]);
		EMPTY_ARRAYS = Collections.unmodifiableMap(emptyArrays);
	}


	private final AnnotationTypeMapping mapping;

	@Nullable
	private final ClassLoader classLoader;

	@Nullable
	private final Object source;

	@Nullable
	private final Object rootAttributes;

	private final ValueExtractor valueExtractor;   // ReflectionUtils::invokeMethod

	private final int aggregateIndex;

	private final boolean useMergedValues;

	@Nullable
	private final Predicate<String> attributeFilter;

	private final int[] resolvedRootMirrors;

	private final int[] resolvedMirrors;


	// 构造方法1
	private TypeMappedAnnotation(AnnotationTypeMapping mapping, @Nullable ClassLoader classLoader,
			@Nullable Object source, @Nullable Object rootAttributes, ValueExtractor valueExtractor,
			int aggregateIndex) {

		this(mapping, 
			classLoader, 
			source,                  // 注解所在的AnnotatedElement对象，比如class/Method
			rootAttributes,          // source上的某个Annotation对象，比如@RequestMapping
			valueExtractor,          // ReflectionUtils::invokeMethod
			aggregateIndex, 
		  	null);
	}

	// 构造方法2
	private TypeMappedAnnotation(AnnotationTypeMapping mapping, 
								 @Nullable ClassLoader classLoader,
								 @Nullable Object source,                 // 注解所在的AnnotatedElement对象，比如class/Method
								 @Nullable Object rootAttributes,         // source上的某个Annotation对象，比如@RequestMapping
								 ValueExtractor valueExtractor,           // ReflectionUtils::invokeMethod
								 int aggregateIndex, 
								 @Nullable int[] resolvedRootMirrors) {

		this.mapping = mapping;
		this.classLoader = classLoader;
		this.source = source;
		this.rootAttributes = rootAttributes;
		this.valueExtractor = valueExtractor;     // ReflectionUtils::invokeMethod
		this.aggregateIndex = aggregateIndex;
		this.useMergedValues = true;
		this.attributeFilter = null; 
		// 一堆别名里面，
		this.resolvedRootMirrors = (resolvedRootMirrors != null ? resolvedRootMirrors :
				                                                  mapping.getRoot().getMirrorSets().resolve(source, rootAttributes, this.valueExtractor));
		this.resolvedMirrors = (getDistance() == 0 ? this.resolvedRootMirrors :
				                                     mapping.getMirrorSets().resolve(source, this, this::getValueForMirrorResolution));
	}

	private TypeMappedAnnotation(AnnotationTypeMapping mapping, @Nullable ClassLoader classLoader,
			@Nullable Object source, @Nullable Object rootAnnotation, ValueExtractor valueExtractor,
			int aggregateIndex, boolean useMergedValues, @Nullable Predicate<String> attributeFilter,
			int[] resolvedRootMirrors, int[] resolvedMirrors) {

		this.classLoader = classLoader;
		this.source = source;
		this.rootAttributes = rootAnnotation;
		this.valueExtractor = valueExtractor;
		this.mapping = mapping;
		this.aggregateIndex = aggregateIndex;
		this.useMergedValues = useMergedValues;
		this.attributeFilter = attributeFilter;
		this.resolvedRootMirrors = resolvedRootMirrors;
		this.resolvedMirrors = resolvedMirrors;
	}


    /**

    */
    // 代码5
	@Nullable
	static <A extends Annotation> TypeMappedAnnotation<A> createIfPossible(AnnotationTypeMapping mapping, 
																		  @Nullable Object source,                 // 注解所在的AnnotatedElement对象，比如class/Method
																		  Annotation annotation,                   // source上的某个Annotation对象，比如@RequestMapping
																		  int aggregateIndex, 
																		  IntrospectionFailureLogger logger) {

        
		// 见代码6
		return createIfPossible(mapping,                          
                                source, 
                                annotation,
                                ReflectionUtils::invokeMethod, 
                                aggregateIndex, 
                                logger);
	}


    /**

    */
    // 代码6
	@Nullable
	private static <A extends Annotation> TypeMappedAnnotation<A> createIfPossible(AnnotationTypeMapping mapping, 
                                                                                   @Nullable Object source,            // 注解所在的AnnotatedElement对象，比如class/Method
                                                                                   @Nullable Object rootAttribute,     // source上的某个Annotation对象，比如@RequestMapping
                                                                                   ValueExtractor valueExtractor,      // ReflectionUtils::invokeMethod
                                                                                   int aggregateIndex, 
                                                                                   IntrospectionFailureLogger logger) {

		try {
			// 见构造方法1
			return new TypeMappedAnnotation<>(mapping, 
											  null, 
											  source, 
											  rootAttribute,
											  valueExtractor, 
											  aggregateIndex);
		}
		catch (Exception ex) {
			AnnotationUtils.rethrowAnnotationConfigurationException(ex);
			if (logger.isEnabled()) {
				String type = mapping.getAnnotationType().getName();
				String item = (mapping.getDistance() == 0 ? "annotation " + type :
						"meta-annotation " + type + " from " + mapping.getRoot().getAnnotationType().getName());
				logger.log("Failed to introspect " + item, source, ex);
			}
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	@Nullable
	static Object extractFromMap(Method attribute, @Nullable Object map) {
		return (map != null ? ((Map<String, ?>) map).get(attribute.getName()) : null);
	}





    /**
	 * 
		From interface: MergedAnnotation 
			Create a new Map instance of the given type that contains all the annotation attributes.
			The adaptations may be used to change the way that values are added.

		Specified by:
		    asMap in interface MergedAnnotation

		Params:
		    factory – a map factory adaptations – the adaptations that should be applied to the annotation values
		Returns:
		    a map containing the attributes and values

	    返回值： map key: 注解的某个属性名
		            value: 属性值
    */              
    // 代码10
	@Override
	public <T extends Map<String, Object>> T asMap(Function<MergedAnnotation<?>, T> factory, 
	                                               Adapt... adaptations) {
	    // 创建AnnotationAttributes对象，其本身实现了Map接口												
		T map = factory.apply(this);

		Assert.state(map != null, "Factory used to create MergedAnnotation Map must not return null");
		// this.mapping: 当前类的成员变量；AnnotationTypeMapping mapping;
		AttributeMethods attributes = this.mapping.getAttributes();

		for (int i = 0; i < attributes.size(); i++) {
			Method attribute = attributes.get(i);
			Object value = (isFiltered(attribute.getName()) ? null :
					        getValue(i, getTypeForMapOptions(attribute, adaptations)));                       // getTypeForMapOptions见代码12
							                                                                                  // getValue见代码13
			if (value != null) {
				map.put(attribute.getName(),
						adaptValueForMapOptions(attribute, value, map.getClass(), factory, adaptations));
			}
		}
		return map;
	}



    /**

    */
    // 代码12
	private Class<?> getTypeForMapOptions(Method attribute,         // 注解属性的 Method 对象
	                                      Adapt[] adaptations) {

		Class<?> attributeType = attribute.getReturnType();     // 对于 org.springframework.context.annotation.ComponentScan.basePackages()，返回值是字符串数组
		Class<?> componentType = (attributeType.isArray() ? attributeType.getComponentType() : attributeType);
		if (Adapt.CLASS_TO_STRING.isIn(adaptations) && componentType == Class.class) {
			return (attributeType.isArray() ? String[].class : String.class);
		}
		return Object.class;
	}


    /**

    */
    // 代码12
	@Nullable
	private <T> T getValue(int attributeIndex, Class<T> type) {
		Method attribute = this.mapping.getAttributes().get(attributeIndex);
		Object value = getValue(attributeIndex,              // 见代码13
			true, 
			false);
		if (value == null) {
			value = attribute.getDefaultValue();
		}
		return adapt(attribute, value, type);
	}








	@SuppressWarnings("unchecked")
	@Nullable
	private <T> T adapt(Method attribute, @Nullable Object value, Class<T> type) {
		if (value == null) {
			return null;
		}
		value = adaptForAttribute(attribute, value);
		type = getAdaptType(attribute, type);
		if (value instanceof Class && type == String.class) {
			value = ((Class<?>) value).getName();
		}
		else if (value instanceof String && type == Class.class) {
			value = ClassUtils.resolveClassName((String) value, getClassLoader());
		}
		else if (value instanceof Class[] && type == String[].class) {
			Class<?>[] classes = (Class<?>[]) value;
			String[] names = new String[classes.length];
			for (int i = 0; i < classes.length; i++) {
				names[i] = classes[i].getName();
			}
			value = names;
		}
		else if (value instanceof String[] && type == Class[].class) {
			String[] names = (String[]) value;
			Class<?>[] classes = new Class<?>[names.length];
			for (int i = 0; i < names.length; i++) {
				classes[i] = ClassUtils.resolveClassName(names[i], getClassLoader());
			}
			value = classes;
		}
		else if (value instanceof MergedAnnotation && type.isAnnotation()) {
			MergedAnnotation<?> annotation = (MergedAnnotation<?>) value;
			value = annotation.synthesize();
		}
		else if (value instanceof MergedAnnotation[] && type.isArray() && type.getComponentType().isAnnotation()) {
			MergedAnnotation<?>[] annotations = (MergedAnnotation<?>[]) value;
			Object array = Array.newInstance(type.getComponentType(), annotations.length);
			for (int i = 0; i < annotations.length; i++) {
				Array.set(array, i, annotations[i].synthesize());
			}
			value = array;
		}
		if (!type.isInstance(value)) {
			throw new IllegalArgumentException("Unable to adapt value of type " +
					value.getClass().getName() + " to " + type.getName());
		}
		return (T) value;
	}

    /**

    */
    // 代码20
	private Object adaptForAttribute(Method attribute,  // 比如注解的一个方法：public abstract boolean org.springframework.context.annotation.Configuration.proxyBeanMethods()
	                                 Object value) {
		Class<?> attributeType = ClassUtils.resolvePrimitiveIfNecessary(attribute.getReturnType());
		if (attributeType.isArray() && !value.getClass().isArray()) {
			Object array = Array.newInstance(value.getClass(), 1);
			Array.set(array, 0, value);
			return adaptForAttribute(attribute, array);
		}
		if (attributeType.isAnnotation()) {
			return adaptToMergedAnnotation(value, (Class<? extends Annotation>) attributeType);
		}
		if (attributeType.isArray() && attributeType.getComponentType().isAnnotation()) {
			MergedAnnotation<?>[] result = new MergedAnnotation<?>[Array.getLength(value)];
			for (int i = 0; i < result.length; i++) {
				result[i] = adaptToMergedAnnotation(Array.get(value, i),
						(Class<? extends Annotation>) attributeType.getComponentType());
			}
			return result;
		}
		if ((attributeType == Class.class && value instanceof String) ||
				(attributeType == Class[].class && value instanceof String[]) ||
				(attributeType == String.class && value instanceof Class) ||
				(attributeType == String[].class && value instanceof Class[])) {
			return value;
		}
		if (attributeType.isArray() && isEmptyObjectArray(value)) {
			return emptyArray(attributeType.getComponentType());
		}
		if (!attributeType.isInstance(value)) {
			throw new IllegalStateException("Attribute '" + attribute.getName() +
					"' in annotation " + getType().getName() + " should be compatible with " +
					attributeType.getName() + " but a " + value.getClass().getName() +
					" value was returned");
		}
		return value;
	}


	private <T> Class<T> getAdaptType(Method attribute, Class<T> type) {
		if (type != Object.class) {
			return type;
		}
		Class<?> attributeType = attribute.getReturnType();
		if (attributeType.isAnnotation()) {
			return (Class<T>) MergedAnnotation.class;
		}
		if (attributeType.isArray() && attributeType.getComponentType().isAnnotation()) {
			return (Class<T>) MergedAnnotation[].class;
		}
		return (Class<T>) ClassUtils.resolvePrimitiveIfNecessary(attributeType);
	}







	private <T extends Map<String, Object>> Object adaptValueForMapOptions(Method attribute, 
																		   Object value,
																		   Class<?> mapType, 
																		   Function<MergedAnnotation<?>, T> factory, 
																		   Adapt[] adaptations) {

		if (value instanceof MergedAnnotation) {
			MergedAnnotation<?> annotation = (MergedAnnotation<?>) value;
			return (Adapt.ANNOTATION_TO_MAP.isIn(adaptations) ?
					annotation.asMap(factory, adaptations) : annotation.synthesize());
		}
		if (value instanceof MergedAnnotation[]) {
			MergedAnnotation<?>[] annotations = (MergedAnnotation<?>[]) value;
			if (Adapt.ANNOTATION_TO_MAP.isIn(adaptations)) {
				Object result = Array.newInstance(mapType, annotations.length);
				for (int i = 0; i < annotations.length; i++) {
					Array.set(result, i, annotations[i].asMap(factory, adaptations));
				}
				return result;
			}
			Object result = Array.newInstance(
					attribute.getReturnType().getComponentType(), annotations.length);
			for (int i = 0; i < annotations.length; i++) {
				Array.set(result, i, annotations[i].synthesize());
			}
			return result;
		}
		return value;
	}



	/**
	 * 

	 * 
	 */
	// 代码50
	protected A createSynthesized() {
		if (getType().isInstance(this.rootAttributes) && !isSynthesizable()) {
			return (A) this.rootAttributes;
		}
		return SynthesizedMergedAnnotationInvocationHandler.createProxy(this, getType());
	}


	/**
	 * 

	 * 
	 */
	// 代码60
	protected <T> T getAttributeValue(String attributeName,     // 待读取的属性名
	                                  Class<T> type) {          // 待读取的属性类型
		// 获取属性的索引值
		int attributeIndex = getAttributeIndex(attributeName, false);   // 见代码61
		return (attributeIndex != -1 ? getValue(attributeIndex, type) : null);   // 见代码62
	}



	// 代码61
	private int getAttributeIndex(String attributeName,      // 待读取的属性名
	                              boolean required) {
		Assert.hasText(attributeName, "Attribute name must not be null");
		int attributeIndex = (isFiltered(attributeName) ? -1 : this.mapping.getAttributes().indexOf(attributeName));
		if (attributeIndex == -1 && required) {
			throw new NoSuchElementException("No attribute named '" + attributeName +
					"' present in merged annotation " + getType().getName());
		}
		return attributeIndex;
	}


	// 代码62
	@Nullable
	private <T> T getValue(int attributeIndex, Class<T> type) {
		// 获取属性方法
		Method attribute = this.mapping.getAttributes().get(attributeIndex);
		Object value = getValue(attributeIndex, true, false);  // 见代码63
		if (value == null) {
			value = attribute.getDefaultValue();
		}
		return adapt(attribute, value, type);
	}


    /**

    */
    // 代码63
	@Nullable
	private Object getValue(int attributeIndex, 
							boolean useConventionMapping,     // true
							boolean forMirrorResolution) {    // false
		AnnotationTypeMapping mapping = this.mapping;
		
		if (this.useMergedValues) {
			int mappedIndex = this.mapping.getAliasMapping(attributeIndex);
			if (mappedIndex == -1 && useConventionMapping) {
				mappedIndex = this.mapping.getConventionMapping(attributeIndex);
			}
			if (mappedIndex != -1) {
				mapping = mapping.getRoot();
				attributeIndex = mappedIndex;
			}
		}


		if (!forMirrorResolution) {
			attributeIndex =
					(mapping.getDistance() != 0 ? this.resolvedMirrors : this.resolvedRootMirrors)[attributeIndex];
		}

		if (attributeIndex == -1) {
			return null;
		}

		if (mapping.getDistance() == 0) {
			Method attribute = mapping.getAttributes().get(attributeIndex);
			// private final ValueExtractor valueExtractor;   // ReflectionUtils::invokeMethod
			Object result = this.valueExtractor.extract(attribute, this.rootAttributes);
			return (result != null ? result : attribute.getDefaultValue());
		}
		return getValueFromMetaAnnotation(attributeIndex, forMirrorResolution);
	}	

}
