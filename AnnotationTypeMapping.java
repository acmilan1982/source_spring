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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.annotation.AnnotationTypeMapping.MirrorSets;
import org.springframework.core.annotation.AnnotationTypeMapping.MirrorSets.MirrorSet;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
    Provides mapping information for a single annotation (or meta-annotation) in the context of a root annotation type.
    Since:
      5.2
    See Also:
      AnnotationTypeMappings
    Author:
      Phillip Webb, Sam Brannen
 */
final class AnnotationTypeMapping {

	private static final MirrorSet[] EMPTY_MIRROR_SETS = new MirrorSet[0];


	// 如果是root，该值为：null	
	@Nullable
	private final AnnotationTypeMapping source;

	// 如果是root，该值为根注解本身对应的AnnotationTypeMapping
	private final AnnotationTypeMapping root;

	// 如果是root，该值为：0
	private final int distance;

	// 当前AnnotationTypeMapping对应的Annotation
	private final Class<? extends Annotation> annotationType;

	private final List<Class<? extends Annotation>> metaTypes;

	@Nullable
	private final Annotation annotation;

	// 当前注解的所有属性方法
	private final AttributeMethods attributes;

	private final MirrorSets mirrorSets;

	// root的属性方法的索引位置
	private final int[] aliasMappings;

	private final int[] conventionMappings;

	private final int[] annotationValueMappings;

	private final AnnotationTypeMapping[] annotationValueSource;

	private final Map<Method, List<Method>> aliasedBy;

	private final boolean synthesizable;

	private final Set<Method> claimedAliases = new HashSet<>();

	/**
 
	 */
	// 构造方法
	AnnotationTypeMapping(@Nullable AnnotationTypeMapping source,       // 如果是root，该值为：null/如果是元注解，则source为元注解所在的子注解AnnotationTypeMapping对象，如PostMapping对应的AnnotationTypeMapping
						  Class<? extends Annotation> annotationType,   // root注解/元注解的Class对象，如：@RequestMapping
						  @Nullable Annotation annotation,              // 如果是root，该值为：null/元注解  
						  Set<Class<? extends Annotation>> visitedAnnotationTypes) {

		// 如果是root，该值为：null / 如果是元注解，该值为元注解所在的子注解				
		this.source = source;
		// 如果是root，该值为根注解本身 / 如果是元注解，该值为元注解所在的子注解(超过2级层次的，则为根注解)	
		this.root = (source != null ? source.getRoot() : this);

		// root的distance为0，其元注解依次+1
		this.distance = (source == null ? 0 : source.getDistance() + 1);
		this.annotationType = annotationType;
		this.metaTypes = merge(
				source != null ? source.getMetaTypes() : null,
				annotationType);
		this.annotation = annotation;


		/*
		 * 
		 *  private final AttributeMethods attributes;
		 *  AttributeMethods持有当前注解的所有属性方法
		 *  在 AnnotationTypeMapping 中，所有的属性都通过它在 AttributeMethods 中的数组(Method[] attributeMethods)下标访问和调用
		 */
		this.attributes = AttributeMethods.forAnnotationType(annotationType);

		// 属性别名与相关的值缓存
		this.mirrorSets = new MirrorSets();                                                    // MirrorSets是当前类的内部类

		// 以下三个数组，数组元素初始值皆为-1
		this.aliasMappings = filledIntArray(this.attributes.size());
		this.conventionMappings = filledIntArray(this.attributes.size());
		this.annotationValueMappings = filledIntArray(this.attributes.size());

		this.annotationValueSource = new AnnotationTypeMapping[this.attributes.size()];
		/**
		 *  Map<Method, List<Method>> aliasedBy: 当前类的实例变量
		 *  key:   被别名的属性方法
		 *  value: 别名的属性方法，一个属性可能有多个别名，因此用一个list来持有
		 * 
		 *  注意：仅扫描当前注解的属性，因此value仅包含当前注解的属性
		 *       但当前注解的属性，可能是其元注解的别名，因此key可能是当前注解的属性，可能是元注解的属性
		 */
		this.aliasedBy = resolveAliasedForTargets();         // 见代码5
		/*
		    // 初始化别名属性，为所有存在别名的属性建立MirrorSet
		 *   
		 * 
		 */
		processAliases();                                    // 代代码9
		/*
		    为当前注解内互为并名的属性建立属性映射
		 *  1. 对于root，其实啥都没做
		 * 
		 */		
		addConventionMappings();
		/*
		    为跨注解互为别名的属性建立属性映射
		 *  1. 对于root，其实啥都没做
		 * 
		 */	
		addConventionAnnotationValues();
		/*
		 *  1. 对于root，其实啥都没做
		 * 
		 */	
		this.synthesizable = computeSynthesizableFlag(visitedAnnotationTypes); // 见代码21
	}


	// 数组元素初始化，全部设置成-1
	// 代码4
	private static int[] filledIntArray(int size) {
		int[] array = new int[size];
		// 数组元素全部设置成-1
		Arrays.fill(array, -1);
		return array;
	}

    /**
	 *  遍历当前注解的所有属性，仅处理被AliasFor的属性
	 *  最后组织成一个Map: 
	 *      key: 被别名的属性方法
	 *      value: 别名的属性方法,一个属性可能有多个别名，因此用一个list来持有
	 *             value只包含当前层级的别名
	 * 
	 * @return
	 */
    // 代码5
	private Map<Method, List<Method>> resolveAliasedForTargets() {
		/**
		 *  key: 被别名的属性方法
		 *  value: 别名的属性方法
		 */
		Map<Method, List<Method>> aliasedBy = new HashMap<>();
		// 遍历所有属性方法
		for (int i = 0; i < this.attributes.size(); i++) {
			Method attribute = this.attributes.get(i);
			// 获取当前属性方法上直接出现的注解AliasFor，不考虑层次结构
			AliasFor aliasFor = AnnotationsScanner.getDeclaredAnnotation(attribute, AliasFor.class);  // 见AnnotationsScanner代码40
			if (aliasFor != null) {
				// 获取当前属性，被别名的属性方法
				Method target = resolveAliasTarget(attribute, aliasFor);                             // 见代码6
				aliasedBy.computeIfAbsent(target, key -> new ArrayList<>()).add(attribute);
			}
		}
		return Collections.unmodifiableMap(aliasedBy);
	}


	// 获取当前属性，被别名的属性方法
	// 代码6
	private Method resolveAliasTarget(Method attribute,        // 属性方法
	                                  AliasFor aliasFor) {     // 属性方法上的@AliasFor
		return resolveAliasTarget(attribute, aliasFor, true);                            // 见代码7
	}

	// 获取当前属性，被别名的属性方法
	// 代码7
	private Method resolveAliasTarget(Method attribute,         // 属性方法
									  AliasFor aliasFor,        // 属性方法上的@AliasFor
									  boolean checkAliasPair) {
		// 	@AliasFor的	value和attribute 互为别名，只能设置其中一个			
		if (StringUtils.hasText(aliasFor.value()) && StringUtils.hasText(aliasFor.attribute())) {
			throw new AnnotationConfigurationException(String.format(
					"In @AliasFor declared on %s, attribute 'attribute' and its alias 'value' " +
					"are present with values of '%s' and '%s', but only one is permitted.",
					AttributeMethods.describe(attribute), aliasFor.attribute(),
					aliasFor.value()));
		}

		// targetAnnotation：被别名属性所在的注解，
		// 来源于注解@AliasFor的annotation属性，比如：RequestMapping.class，也可能是当前注解对象
		Class<? extends Annotation> targetAnnotation = aliasFor.annotation();
		// @AliasFor的annotation属性，如果是默认值：Annotation.class，则表示被别名的属性在当前注解对象中
		if (targetAnnotation == Annotation.class) {
			targetAnnotation = this.annotationType;
		}

		// 注解@AliasFor的attribute属性，如果未设置，使用value指定的值
		String targetAttributeName = aliasFor.attribute();
		if (!StringUtils.hasLength(targetAttributeName)) {
			targetAttributeName = aliasFor.value();
		}

		// 如果未指定attribute(value)属性，则使用属性方法的方法名(表示同名属性)
		if (!StringUtils.hasLength(targetAttributeName)) {
			targetAttributeName = attribute.getName();
		}

		// target：返回值
		// 获取被别名属性的属性方法(与当前属性在同一个注解中，或不同注解对象)
		Method target = AttributeMethods.forAnnotationType(targetAnnotation).get(targetAttributeName);
		if (target == null) {
			if (targetAnnotation == this.annotationType) {
				throw new AnnotationConfigurationException(String.format(
						"@AliasFor declaration on %s declares an alias for '%s' which is not present.",
						AttributeMethods.describe(attribute), targetAttributeName));
			}
			throw new AnnotationConfigurationException(String.format(
					"%s is declared as an @AliasFor nonexistent %s.",
					StringUtils.capitalize(AttributeMethods.describe(attribute)),
					AttributeMethods.describe(targetAnnotation, targetAttributeName)));
		}
		if (target.equals(attribute)) {
			throw new AnnotationConfigurationException(String.format(
					"@AliasFor declaration on %s points to itself. " +
					"Specify 'annotation' to point to a same-named attribute on a meta-annotation.",
					AttributeMethods.describe(attribute)));
		}
		if (!isCompatibleReturnType(attribute.getReturnType(), target.getReturnType())) {
			throw new AnnotationConfigurationException(String.format(
					"Misconfigured aliases: %s and %s must declare the same return type.",
					AttributeMethods.describe(attribute),
					AttributeMethods.describe(target)));
		}

		if (isAliasPair(target) && checkAliasPair) {
			AliasFor targetAliasFor = target.getAnnotation(AliasFor.class);
			if (targetAliasFor != null) {
				Method mirror = resolveAliasTarget(target, targetAliasFor, false);
				if (!mirror.equals(attribute)) {
					throw new AnnotationConfigurationException(String.format(
							"%s must be declared as an @AliasFor %s, not %s.",
							StringUtils.capitalize(AttributeMethods.describe(target)),
							AttributeMethods.describe(attribute), AttributeMethods.describe(mirror)));
				}
			}
		}
		return target;
	}



	// 代码9	
	private void processAliases() {
		List<Method> aliases = new ArrayList<>();
		// 遍历当前注解的所有属性方法
		for (int i = 0; i < this.attributes.size(); i++) {
			aliases.clear();
			// 此时，aliases持有一个属性方法
			aliases.add(this.attributes.get(i));

			// 从整个层次结构中寻找别名属性
			collectAliases(aliases);                        // 见代码10
			// aliases.size() > 1，表示当前属性在整个层级结构中存在别名
			if (aliases.size() > 1) {
				processAliases(i, aliases);                 // 见代码11
			}
		}
	}

	// 从整个层级结构中寻找当前属性的别名
	// 别名可能还有别名，所以最后的aliases，是整个层级结构中所有相关别名的集合
	// 代码10
	private void collectAliases(List<Method> aliases) {
		AnnotationTypeMapping mapping = this;
		while (mapping != null) {
			int size = aliases.size();

			for (int j = 0; j < size; j++) {
				// step1: 从当前层级获取当前属性的别名，比如 RequestMapping中，path是value的别名
				List<Method> additional = mapping.aliasedBy.get(aliases.get(j));
				// 找到别名属性，加入aliases，因为该别名，可能也有别名
				if (additional != null) {
					aliases.addAll(additional);
				}
			}
			// 当前层级找完了，往下找
			mapping = mapping.source;
		}
	}


	// 代码11
	private void processAliases(int attributeIndex,         // 当前属性在AttributeMethods中的索引位置
	                            List<Method> aliases) {     // 属性自己，再加上从整个层次结构中寻找到别名属性
		// 尝试从root中，寻找aliases中的属性，找到1个即可
		// rootAttributeIndex表示当前属性在根对象中的索引位置
		int rootAttributeIndex = getFirstRootAttributeIndex(aliases);  // 见代码12
		AnnotationTypeMapping mapping = this;

		while (mapping != null) {

			// root 不执行该方法块
			// 如果别名在root中，且当前注解对象不是root， 则当前注解对象的属性，指向根对象的属性方法索引位置
			if (rootAttributeIndex != -1 && mapping != this.root) {
				for (int i = 0; i < mapping.attributes.size(); i++) {
					if (aliases.contains(mapping.attributes.get(i))) {
						mapping.aliasMappings[i] = rootAttributeIndex;   // 别名索引指向root中的AttributeMethods中的索引位置
					}
				}
			}

			mapping.mirrorSets.updateFrom(aliases);        // 见 MirrorSets 代码1
			mapping.claimedAliases.addAll(aliases);

			// root 不执行该方法块
			if (mapping.annotation != null) {

				int[] resolvedMirrors = mapping.mirrorSets.resolve(null,                                  // 见MirrorSets代码10
																   mapping.annotation,
																   ReflectionUtils::invokeMethod);
				for (int i = 0; i < mapping.attributes.size(); i++) {
					if (aliases.contains(mapping.attributes.get(i))) {
						this.annotationValueMappings[attributeIndex] = resolvedMirrors[i];
						this.annotationValueSource[attributeIndex] = mapping;
					}
				}
			}
			mapping = mapping.source;
		}

	}

	// 尝试从root中，寻找aliases中的属性，找到1个即可   
    // 代码12
	private int getFirstRootAttributeIndex(Collection<Method> aliases) {
		// root的所有属性方法
		AttributeMethods rootAttributes = this.root.getAttributes();
		// 遍历root的所有属性方法
		for (int i = 0; i < rootAttributes.size(); i++) {
			if (aliases.contains(rootAttributes.get(i))) {
				return i;
			}
		}
		return -1;
	}






    // 代码15
	private void addConventionMappings() {
		if (this.distance == 0) {
			return;
		}
		AttributeMethods rootAttributes = this.root.getAttributes();
		int[] mappings = this.conventionMappings;

		for (int i = 0; i < mappings.length; i++) {
			String name = this.attributes.get(i).getName();
			MirrorSet mirrors = getMirrorSets().getAssigned(i);
			int mapped = rootAttributes.indexOf(name);
			if (!MergedAnnotation.VALUE.equals(name) && mapped != -1) {
				mappings[i] = mapped;
				if (mirrors != null) {
					for (int j = 0; j < mirrors.size(); j++) {
						mappings[mirrors.getAttributeIndex(j)] = mapped;
					}
				}
			}
		}
	}

    // 代码17
	private void addConventionAnnotationValues() {
		// 遍历所有属性方法
		for (int i = 0; i < this.attributes.size(); i++) {
			Method attribute = this.attributes.get(i);
			// 判断当前属性是否为value
			boolean isValueAttribute = MergedAnnotation.VALUE.equals(attribute.getName());
			AnnotationTypeMapping mapping = this;

			while (mapping != null && mapping.distance > 0) {
				int mapped = mapping.getAttributes().indexOf(attribute.getName());
				if (mapped != -1 && isBetterConventionAnnotationValue(i, isValueAttribute, mapping)) {
					this.annotationValueMappings[i] = mapped;
					this.annotationValueSource[i] = mapping;
				}
				mapping = mapping.source;
			}
		}
	}

    // 代码18
	private boolean isBetterConventionAnnotationValue(int index, boolean isValueAttribute,
			AnnotationTypeMapping mapping) {

		if (this.annotationValueMappings[index] == -1) {
			return true;
		}
		int existingDistance = this.annotationValueSource[index].distance;
		return !isValueAttribute && existingDistance > mapping.distance;
	}



    // 代码21
	@SuppressWarnings("unchecked")
	private boolean computeSynthesizableFlag(Set<Class<? extends Annotation>> visitedAnnotationTypes) {
		// Track that we have visited the current annotation type.
		visitedAnnotationTypes.add(this.annotationType);

		// Uses @AliasFor for local aliases?
		for (int index : this.aliasMappings) {
			if (index != -1) {
				return true;
			}
		}

		// Uses @AliasFor for attribute overrides in meta-annotations?
		if (!this.aliasedBy.isEmpty()) {
			return true;
		}

		// Uses convention-based attribute overrides in meta-annotations?
		for (int index : this.conventionMappings) {
			if (index != -1) {
				return true;
			}
		}

		// Has nested annotations or arrays of annotations that are synthesizable?
		if (getAttributes().hasNestedAnnotation()) {
			AttributeMethods attributeMethods = getAttributes();
			for (int i = 0; i < attributeMethods.size(); i++) {
				Method method = attributeMethods.get(i);
				Class<?> type = method.getReturnType();
				if (type.isAnnotation() || (type.isArray() && type.getComponentType().isAnnotation())) {
					Class<? extends Annotation> annotationType =
							(Class<? extends Annotation>) (type.isAnnotation() ? type : type.getComponentType());
					// Ensure we have not yet visited the current nested annotation type, in order
					// to avoid infinite recursion for JVM languages other than Java that support
					// recursive annotation definitions.
					if (visitedAnnotationTypes.add(annotationType)) {
						AnnotationTypeMapping mapping =
								AnnotationTypeMappings.forAnnotationType(annotationType, visitedAnnotationTypes).get(0);
						if (mapping.isSynthesizable()) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}	

    // 代码25
	private static boolean isEquivalentToDefaultValue(Method attribute, Object value,
			ValueExtractor valueExtractor) {

		return areEquivalent(attribute.getDefaultValue(), value, valueExtractor);
	}	

    // 代码26
	private static boolean areEquivalent(@Nullable Object value,           // 属性的默认值 
	                                     @Nullable Object extractedValue,  // 实际提取的属性值
			                             ValueExtractor valueExtractor) {

		if (ObjectUtils.nullSafeEquals(value, extractedValue)) {
			return true;
		}
		if (value instanceof Class && extractedValue instanceof String) {
			return areEquivalent((Class<?>) value, (String) extractedValue);
		}
		if (value instanceof Class[] && extractedValue instanceof String[]) {
			return areEquivalent((Class[]) value, (String[]) extractedValue);
		}
		if (value instanceof Annotation) {
			return areEquivalent((Annotation) value, extractedValue, valueExtractor);
		}
		return false;
	}

}
