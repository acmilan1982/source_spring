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
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
	Scanner to search for relevant annotations in the annotation hierarchy of an AnnotatedElement.
	Since:
	  5.2
	See Also:
	  AnnotationsProcessor
	Author:
	  Phillip Webb, Sam Brannen
 */
abstract class AnnotationsScanner {

	private static final Annotation[] NO_ANNOTATIONS = {};

	private static final Method[] NO_METHODS = {};

    
	/*
	 * key:    AnnotatedElement对象
	 * value:  getDeclaredAnnotations()
	 */
	private static final Map<AnnotatedElement, Annotation[]> declaredAnnotationCache = new ConcurrentReferenceHashMap<>(256);

	private static final Map<Class<?>, Method[]> baseTypeMethodsCache = new ConcurrentReferenceHashMap<>(256);

	/**

	 */
	// 构造方法
	private AnnotationsScanner() {
	}

	/**
         判断当前对象是否为jdk的对象，
		 或者是否没有任何注解，
		 处理过程中，会	获取当前对象声明的所有注解，并缓存至AnnotationsScanner
				       获取每个注解的的所有属性方法，并缓存至AttributeMethods

		  
	 */
	// 代码5
	static boolean isKnownEmpty(AnnotatedElement source,               // 比如Class对象
	                            SearchStrategy searchStrategy) {       // SearchStrategy对象，如：SearchStrategy.INHERITED_ANNOTATIONS,
		// 对于Class对象，判断当前类是否为JDK类库中的类，或者  org.springframework.core.Ordered，这些类不做任何处理					
		if (hasPlainJavaAnnotationsOnly(source)) {  // 见代码6
			return true;
		}

		if (searchStrategy == SearchStrategy.DIRECT || isWithoutHierarchy(source, searchStrategy)) {
			if (source instanceof Method && ((Method) source).isBridge()) {
				return false;
			}

			/**
				 获取当前对象声明的所有注解，并缓存至AnnotationsScanner
				 获取每个注解的的所有属性方法，并缓存至AttributeMethods
			*/
			return getDeclaredAnnotations(source, false).length == 0;  //见代码10
		}

		return false;
	}




	/**
         对于Class对象，判断当前类是否为JDK类库中的类，或者  org.springframework.core.Ordered

	 */
	// 代码6
	static boolean hasPlainJavaAnnotationsOnly(@Nullable Object annotatedElement) {
		if (annotatedElement instanceof Class) {
			// 判断当前类是否为JDK类库中的类，或者  org.springframework.core.Ordered
			return hasPlainJavaAnnotationsOnly((Class<?>) annotatedElement);   // 见代码7
		}
		else if (annotatedElement instanceof Member) {
			return hasPlainJavaAnnotationsOnly(((Member) annotatedElement).getDeclaringClass());
		}
		else {
			return false;
		}
	}	

	/**
          判断当前类是否为JDK类库中的类，或者  org.springframework.core.Ordered
	 */
	// 代码7
	static boolean hasPlainJavaAnnotationsOnly(Class<?> type) {
		return (type.getName().startsWith("java.") || type == Ordered.class);
	}



	/**

	 */
	// 代码9
	private static boolean isWithoutHierarchy(AnnotatedElement source, SearchStrategy searchStrategy) {
		if (source == Object.class) {
			return true;
		}
		if (source instanceof Class) {
			Class<?> sourceClass = (Class<?>) source;
			boolean noSuperTypes = (sourceClass.getSuperclass() == Object.class &&
					                sourceClass.getInterfaces().length == 0);

			return (searchStrategy == SearchStrategy.TYPE_HIERARCHY_AND_ENCLOSING_CLASSES ? noSuperTypes &&
					sourceClass.getEnclosingClass() == null : noSuperTypes);
		}
		if (source instanceof Method) {
			Method sourceMethod = (Method) source;
			return (Modifier.isPrivate(sourceMethod.getModifiers()) ||
					isWithoutHierarchy(sourceMethod.getDeclaringClass(), searchStrategy));
		}

		return true;
	}
	
	/**

         1. 读取当前AnnotatedElement上，所有的直接出现注解，不包括继承的(source.getDeclaredAnnotations())
		    排除基本注解("java.lang", "org.springframework.lang"  包下)
		 2. 缓存 AnnotatedElement->所有注解
		 3. 获取每个注解的的所有属性方法，并缓存至AttributeMethods

		 只搜索当前层

	 */
	// 代码10
	static Annotation[] getDeclaredAnnotations(AnnotatedElement source,  // AnnotatedElement对象，Class/Method
	                                           boolean defensive) {  // false
		boolean cached = false;
	    /*
		 * 	declaredAnnotationCache: 当前类的缓存 private static final Map<AnnotatedElement, Annotation[]> declaredAnnotationCache = new ConcurrentReferenceHashMap<>(256);
		 */
		Annotation[] annotations = declaredAnnotationCache.get(source);
		// 判断是否已缓存
		if (annotations != null) {
			cached = true;
		}
		else {
			// 获取当前 AnnotatedElement 直接声明的注解(不包括继承的)
			annotations = source.getDeclaredAnnotations();

			if (annotations.length != 0) {
				boolean allIgnored = true;
				// 遍历 当前 AnnotatedElement 直接声明的注解
				for (int i = 0; i < annotations.length; i++) {
					Annotation annotation = annotations[i];
					/*
					 *    1. isIgnorable:判断当前注解是否需要被忽略 ，"java.lang", "org.springframework.lang"  包下的注解会被忽略
					 *    2. AttributeMethods.forAnnotationType：获取当前注解的所有属性方法，并缓存
					 */
					if (isIgnorable(annotation.annotationType()) ||                                               // 见代码11
						!AttributeMethods.forAnnotationType(annotation.annotationType()).isValid(annotation)) {   // 见AttributeMethods代码10
						annotations[i] = null;
					}
					else {
						allIgnored = false;
					}
				}

				annotations = (allIgnored ? NO_ANNOTATIONS : annotations);
				if (source instanceof Class || source instanceof Member) {
					declaredAnnotationCache.put(source, annotations);
					cached = true;
				}
			}
		}


		if (!defensive || annotations.length == 0 || !cached) {
			return annotations;
		}
		return annotations.clone();
	}

	/**
          判断当前注解是否需要被忽略 ，"java.lang", "org.springframework.lang"  包下的注解会被忽略
	 */
	// 代码11
	private static boolean isIgnorable(Class<?> annotationType) {
		return AnnotationFilter.PLAIN.matches(annotationType);
	}




	/**
		Scan the hierarchy of the specified element for relevant annotations and call the processor as required.
		Params:
		context – an optional context object that will be passed back to the processor source – the source element to scan searchStrategy – the search strategy to use processor – the processor that receives the annotations
		Returns:
		the result of AnnotationsProcessor.finish(Object)

		搜索指定元素的继承体系，以获取指定的注解，必要时调用 processor

	 */
	// 代码15
	@Nullable
	static <C, R> R scan(C context,                               // 待搜索的注解，如org.springframework.context.annotation.Configuration
						 AnnotatedElement source,                 // 待搜索注解所在的AnnotatedElement对象,如Class/Method
						 SearchStrategy searchStrategy,           // SearchStrategy.INHERITED_ANNOTATIONS
						 AnnotationsProcessor<C, R> processor) {  // MergedAnnotationFinder

		R result = process(context, source, searchStrategy, processor);  // 见代码16
		return processor.finish(result);
	}


	/**



	 */
	// 代码16
	@Nullable
	private static <C, R> R process(C context, AnnotatedElement source,
			SearchStrategy searchStrategy, AnnotationsProcessor<C, R> processor) {

		if (source instanceof Class) {
			return processClass(context, (Class<?>) source, searchStrategy, processor);  // 见代码17
		}
		if (source instanceof Method) {
			return processMethod(context, (Method) source, searchStrategy, processor);   // 见代码18
		}
		return processElement(context, source, processor);
	}
 
	/**

	 */
	// 代码17
	@Nullable
	private static <C, R> R processClass(C context,                               // 待搜索的注解，如org.springframework.context.annotation.Configuration
										 Class<?> source,                         // 待搜索注解所在的AnnotatedElement对象,Class/Method
										 SearchStrategy searchStrategy,           // SearchStrategy.INHERITED_ANNOTATIONS
										 AnnotationsProcessor<C, R> processor) {  // MergedAnnotationFinder

		switch (searchStrategy) {
			case DIRECT:
				return processElement(context, source, processor);
			case INHERITED_ANNOTATIONS:
				return processClassInheritedAnnotations(context, source, searchStrategy, processor);  // 见代码18
			case SUPERCLASS:
				return processClassHierarchy(context, source, processor, false, false);
			case TYPE_HIERARCHY:
				return processClassHierarchy(context, source, processor, true, false);      // 见代码22
			case TYPE_HIERARCHY_AND_ENCLOSING_CLASSES:
				return processClassHierarchy(context, source, processor, true, true);
		}
		throw new IllegalStateException("Unsupported search strategy " + searchStrategy);
	}

	/**

	 */
	// 代码18
	@Nullable
	private static <C, R> R process(C context,                                  
									AnnotatedElement source,                    
									SearchStrategy searchStrategy,              // SearchStrategy.INHERITED_ANNOTATIONS
									AnnotationsProcessor<C, R> processor) {     // MergedAnnotationFinder

		if (source instanceof Class) {
			return processClass(context, (Class<?>) source, searchStrategy, processor);
		}
		if (source instanceof Method) {
			return processMethod(context, (Method) source, searchStrategy, processor);  // 见代码19
		}
		return processElement(context, source, processor);
	}
	/**

	 */
	// 代码18
	private static <C, R> R processMethod(C context,                               // 待搜索的注解，如org.springframework.context.annotation.Configuration
										  Method source,                           // 待搜索注解所在的AnnotatedElement对象,Class/Method
										  SearchStrategy searchStrategy,           // SearchStrategy.INHERITED_ANNOTATIONS
										  AnnotationsProcessor<C, R> processor) {  // MergedAnnotationFinder

		switch (searchStrategy) {
			case DIRECT:
			case INHERITED_ANNOTATIONS:
				return processMethodInheritedAnnotations(context, source, processor);
			case SUPERCLASS:
				return processMethodHierarchy(context, new int[] {0}, source.getDeclaringClass(),
						processor, source, false);
			case TYPE_HIERARCHY:
			case TYPE_HIERARCHY_AND_ENCLOSING_CLASSES:
				return processMethodHierarchy(context,                                            // 见代码35
											  new int[] {0}, 
											  source.getDeclaringClass(),
											  processor, 
											  source, 
											  true);
		}
		throw new IllegalStateException("Unsupported search strategy " + searchStrategy);
	}


	/**

	 */
	// 代码20
	@Nullable
	private static <C, R> R processClassInheritedAnnotations(C context,                                // org.springframework.context.annotation.Configuration
															 Class<?> source,                          // 注解所在的Class对象
															 SearchStrategy searchStrategy,            // SearchStrategy.INHERITED_ANNOTATIONS
															 AnnotationsProcessor<C, R> processor) {   // MergedAnnotationFinder

		try {
            // 判断当前类是否有继承体系
			if (isWithoutHierarchy(source, searchStrategy)) {     // 见代码19
				return processElement(context, source, processor); // 见代码30
			}
			Annotation[] relevant = null;
			int remaining = Integer.MAX_VALUE;
			int aggregateIndex = 0;
			Class<?> root = source;

			while (source != null && source != Object.class && remaining > 0 &&
					!hasPlainJavaAnnotationsOnly(source)) {
				R result = processor.doWithAggregate(context, aggregateIndex);
				if (result != null) {
					return result;
				}
				
				// 获取当前Class对象的直接的注解
				Annotation[] declaredAnnotations = getDeclaredAnnotations(source, true);
				if (relevant == null && declaredAnnotations.length > 0) {
					// 获取所有注解，包括继承的
					relevant = root.getAnnotations();
					remaining = relevant.length;
				}


				for (int i = 0; i < declaredAnnotations.length; i++) {
					if (declaredAnnotations[i] != null) {
						boolean isRelevant = false;
						for (int relevantIndex = 0; relevantIndex < relevant.length; relevantIndex++) {
							if (relevant[relevantIndex] != null &&
									declaredAnnotations[i].annotationType() == relevant[relevantIndex].annotationType()) {
								isRelevant = true;
								relevant[relevantIndex] = null;
								remaining--;
								break;
							}
						}
						if (!isRelevant) {
							declaredAnnotations[i] = null;
						}
					}
				}
				result = processor.doWithAnnotations(context, aggregateIndex, source, declaredAnnotations);
				if (result != null) {
					return result;
				}
				source = source.getSuperclass();
				aggregateIndex++;
			}
		}
		catch (Throwable ex) {
			AnnotationUtils.handleIntrospectionFailure(source, ex);
		}
		return null;
	}

 



	// 代码22
	@Nullable
	private static <C, R> R processClassHierarchy(C context,                               // 待搜索的注解，比如： org.springframework.context.annotation.Configuration
	                                              Class<?> source,                         // 注解所在的Class对象
												  AnnotationsProcessor<C, R> processor,    // MergedAnnotationFinder
												  boolean includeInterfaces,               // true
												  boolean includeEnclosing) {              // false

		return processClassHierarchy(context,                                             // 见代码23
									 new int[] {0}, 
									 source, 
									 processor,
									 includeInterfaces, 
									 includeEnclosing);
	}

	// 代码23
	@Nullable
	private static <C, R> R processClassHierarchy(C context,                               // 待搜索的注解，如org.springframework.context.annotation.Configuration
												  int[] aggregateIndex,
												  Class<?> source,                         // 待搜索注解所在的AnnotatedElement对象,Class/Method
												  AnnotationsProcessor<C, R> processor, 
												  boolean includeInterfaces,               // true
												  boolean includeEnclosing) {              // false

		try {
			R result = processor.doWithAggregate(context, aggregateIndex[0]);
			if (result != null) {
				return result;
			}
			if (hasPlainJavaAnnotationsOnly(source)) {
				return null;
			}
			// 获取当前Class上的所有注解，不包含继承的注解
			Annotation[] annotations = getDeclaredAnnotations(source, false);              // 见代码10
			result = processor.doWithAnnotations(context, aggregateIndex[0], source, annotations);   // 见MergedAnnotationFinder代码5
			
			if (result != null) {
				return result;
			}
			aggregateIndex[0]++;
			if (includeInterfaces) {
				for (Class<?> interfaceType : source.getInterfaces()) {
					R interfacesResult = processClassHierarchy(context, aggregateIndex,
						interfaceType, processor, true, includeEnclosing);
					if (interfacesResult != null) {
						return interfacesResult;
					}
				}
			}
			Class<?> superclass = source.getSuperclass();
			if (superclass != Object.class && superclass != null) {
				R superclassResult = processClassHierarchy(context, aggregateIndex,
					superclass, processor, includeInterfaces, includeEnclosing);
				if (superclassResult != null) {
					return superclassResult;
				}
			}
			if (includeEnclosing) {
				// Since merely attempting to load the enclosing class may result in
				// automatic loading of sibling nested classes that in turn results
				// in an exception such as NoClassDefFoundError, we wrap the following
				// in its own dedicated try-catch block in order not to preemptively
				// halt the annotation scanning process.
				try {
					Class<?> enclosingClass = source.getEnclosingClass();
					if (enclosingClass != null) {
						R enclosingResult = processClassHierarchy(context, aggregateIndex,
							enclosingClass, processor, includeInterfaces, true);
						if (enclosingResult != null) {
							return enclosingResult;
						}
					}
				}
				catch (Throwable ex) {
					AnnotationUtils.handleIntrospectionFailure(source, ex);
				}
			}
		}
		catch (Throwable ex) {
			AnnotationUtils.handleIntrospectionFailure(source, ex);
		}
		return null;
	}

	
	/**

	 */
	// 代码30
	@Nullable
	private static <C, R> R processElement(C context,                                      // org.springframework.context.annotation.Configuration
	                                       AnnotatedElement source,                        // 注解所在的Class对象
			                               AnnotationsProcessor<C, R> processor) {         // MergedAnnotationFinder

		try {
			R result = processor.doWithAggregate(context, 0);
			// 
			return (result != null ? result : processor.doWithAnnotations(context,          // doWithAnnotationsd见MergedAnnotationFinder代码5
																		  0, 
																		  source, 
																		  getDeclaredAnnotations(source, false)));  // 可以从缓存中获取当前 AnnotatedElement 的所有注解对象
		}														  
		catch (Throwable ex) {
			AnnotationUtils.handleIntrospectionFailure(source, ex);
		}
		return null;
	}

 	/**

	 */
	// 代码35
	@Nullable
	private static <C, R> R processMethodHierarchy(C context,                  // 待搜索的注解，如org.springframework.context.annotation.Configuration
												   int[] aggregateIndex,       
												   Class<?> sourceClass,        // 待搜索注解所在的AnnotatedElement对象,Class 
											       AnnotationsProcessor<C, R> processor, 
												   Method rootMethod,
												   boolean includeInterfaces) {  // true

		try {
			R result = processor.doWithAggregate(context, aggregateIndex[0]);
			if (result != null) {
				return result;
			}

			if (hasPlainJavaAnnotationsOnly(sourceClass)) {
				return null;
			}

			boolean calledProcessor = false;
			if (sourceClass == rootMethod.getDeclaringClass()) {
				result = processMethodAnnotations(context, aggregateIndex[0],        // 见代码37
					rootMethod, processor);
				calledProcessor = true;
				if (result != null) {
					return result;
				}
			}
			else {
				for (Method candidateMethod : getBaseTypeMethods(context, sourceClass)) {
					if (candidateMethod != null && isOverride(rootMethod, candidateMethod)) {
						result = processMethodAnnotations(context, aggregateIndex[0],
							candidateMethod, processor);
						calledProcessor = true;
						if (result != null) {
							return result;
						}
					}
				}
			}
			if (Modifier.isPrivate(rootMethod.getModifiers())) {
				return null;
			}
			if (calledProcessor) {
				aggregateIndex[0]++;
			}
			if (includeInterfaces) {
				for (Class<?> interfaceType : sourceClass.getInterfaces()) {
					R interfacesResult = processMethodHierarchy(context, aggregateIndex,
						interfaceType, processor, rootMethod, true);
					if (interfacesResult != null) {
						return interfacesResult;
					}
				}
			}
			Class<?> superclass = sourceClass.getSuperclass();
			if (superclass != Object.class && superclass != null) {
				R superclassResult = processMethodHierarchy(context, aggregateIndex,
					superclass, processor, rootMethod, includeInterfaces);
				if (superclassResult != null) {
					return superclassResult;
				}
			}
		}
		catch (Throwable ex) {
			AnnotationUtils.handleIntrospectionFailure(rootMethod, ex);
		}
		return null;
	}

 	/**

	 */
	// 代码37
	private static <C, R> R processMethodAnnotations(C context,              // 待搜索的注解，如org.springframework.context.annotation.Configuration
													 int aggregateIndex,     // 聚合索引，从0开始
												 	 Method source,          // 待搜索的注解所在的方法
													 AnnotationsProcessor<C, R> processor) {

		/**

			1. 读取当前AnnotatedElement上，所有的直接出现注解，不包括继承的(source.getDeclaredAnnotations())
				排除基本注解("java.lang", "org.springframework.lang"  包下)
			2. 缓存 AnnotatedElement->所有注解
			3. 获取每个注解的的所有属性方法，并缓存至AttributeMethods

			只搜索当前层

		*/										
		Annotation[] annotations = getDeclaredAnnotations(source, false);             // 见代码10
		R result = processor.doWithAnnotations(context, aggregateIndex, source, annotations);   // 见MergedAnnotationFinder代码5
		if (result != null) {
			return result;
		}
		Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(source);
		if (bridgedMethod != source) {
			Annotation[] bridgedAnnotations = getDeclaredAnnotations(bridgedMethod, true);
			for (int i = 0; i < bridgedAnnotations.length; i++) {
				if (ObjectUtils.containsElement(annotations, bridgedAnnotations[i])) {
					bridgedAnnotations[i] = null;
				}
			}
			return processor.doWithAnnotations(context, aggregateIndex, source, bridgedAnnotations);
		}
		return null;
	}



 	/**
         指定AnnotatedElement上搜索直接出现的注解，不考虑层次结构
	 */
	// 代码40
	static <A extends Annotation> A getDeclaredAnnotation(AnnotatedElement source,        // 指定AnnotatedElement上
	                                                      Class<A> annotationType) {      // 待搜索的注解
		// 获取当前	AnnotatedElement 上的所有注解												
		Annotation[] annotations = getDeclaredAnnotations(source, false);       // 见代码10
		for (Annotation annotation : annotations) {
			if (annotation != null && annotationType == annotation.annotationType()) {
				return (A) annotation;
			}
		}
		return null;
	}

}
