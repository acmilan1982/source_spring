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

package org.springframework.context.annotation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionDefaults;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

/**
	A bean definition scanner that detects bean candidates on the classpath, 
	registering corresponding bean definitions with a given registry (BeanFactory or ApplicationContext).
	Candidate classes are detected through configurable type filters. The default filters include classes that are annotated with Spring's @Component, @Repository, @Service, or @Controller stereotype.
	Also supports Java EE 6's javax.annotation.ManagedBean and JSR-330's javax.inject.Named annotations, if available.
	Since:
	2.5
	See Also:
	  AnnotationConfigApplicationContext.scan, org.springframework.stereotype.Component, org.springframework.stereotype.Repository, org.springframework.stereotype.Service, org.springframework.stereotype.Controller
	Author:
	  Mark Fisher, Juergen Hoeller, Chris Beams
	

 */
public class ClassPathBeanDefinitionScanner extends ClassPathScanningCandidateComponentProvider {

	private final BeanDefinitionRegistry registry;

	private BeanDefinitionDefaults beanDefinitionDefaults = new BeanDefinitionDefaults();

	@Nullable
	private String[] autowireCandidatePatterns;

	private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	private boolean includeAnnotationConfig = true;


	/**
		Create a new ClassPathBeanDefinitionScanner for the given bean factory.

		Params:
		  registry – the BeanFactory to load bean definitions into, in the form of a BeanDefinitionRegistry
	 */
    // 构造方法1
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry) {
		this(registry, true);   // 见构造方法2
	}

	/**
		Create a new ClassPathBeanDefinitionScanner for the given bean factory.
		If the passed-in bean factory does not only implement the BeanDefinitionRegistry interface but also the ResourceLoader interface, it will be used as default ResourceLoader as well. 
		This will usually be the case for org.springframework.context.ApplicationContext implementations.

		If given a plain BeanDefinitionRegistry, the default ResourceLoader will be a org.springframework.core.io.support.PathMatchingResourcePatternResolver.

		If the passed-in bean factory also implements EnvironmentCapable its environment will be used by this reader. Otherwise, the reader will initialize and use a StandardEnvironment. 
		All ApplicationContext implementations are EnvironmentCapable, while normal BeanFactory implementations are not.
		Params:
		  registry – the BeanFactory to load bean definitions into, in the form of a BeanDefinitionRegistry useDefaultFilters – whether to include the default filters for the @Component, @Repository, @Service, and @Controller stereotype annotations
		See Also:
		  setResourceLoader, setEnvironment
	 */
    // 构造方法2
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, 
	                                      boolean useDefaultFilters) {  // true
		this(registry, useDefaultFilters, getOrCreateEnvironment(registry));   // 见构造方法3
	}

	/**
		Create a new ClassPathBeanDefinitionScanner for the given bean factory and using the given Environment when evaluating bean definition profile metadata.
		If the passed-in bean factory does not only implement the BeanDefinitionRegistry interface but also the ResourceLoader interface, it will be used as default ResourceLoader as well. This will usually be the case for org.springframework.context.ApplicationContext implementations.
		If given a plain BeanDefinitionRegistry, the default ResourceLoader will be a org.springframework.core.io.support.PathMatchingResourcePatternResolver.
		Params:
		registry – the BeanFactory to load bean definitions into, in the form of a BeanDefinitionRegistry useDefaultFilters – whether to include the default filters for the @Component, @Repository, @Service, and @Controller stereotype annotations environment – the Spring Environment to use when evaluating bean definition profile metadata
		Since:
		3.1
		See Also:
		setResourceLoader
	 */
    // 构造方法3
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, 
	                                      boolean useDefaultFilters,  // true
			                              Environment environment) {  // StandardEnvironment

		this(registry, useDefaultFilters, environment,
				(registry instanceof ResourceLoader ? (ResourceLoader) registry : null));
	}
 
    /*
		Create a new ClassPathBeanDefinitionScanner for the given bean factory and using the given Environment when evaluating bean definition profile metadata.

		Params:
		  registry – the BeanFactory to load bean definitions into, in the form of a BeanDefinitionRegistry useDefaultFilters – whether to include the default filters for the @Component, @Repository, @Service, and @Controller stereotype annotations environment – the Spring Environment to use when evaluating bean definition profile metadata resourceLoader – the ResourceLoader to use
		
		Since:
		  4.3.6
    */
    // 构造方法5 	
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry,            // AnnotationConfigApplicationContext
										  boolean useDefaultFilters,                  // true
										                                              // @ComponentScan的属性:useDefaultFilters，默认为true 
										  Environment environment,                    // StandardEnvironment
										  @Nullable ResourceLoader resourceLoader) {  // AnnotationConfigApplicationContext，其实现了 ResourceLoader 接口

		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		this.registry = registry;

		if (useDefaultFilters) {
			// Register the default filter for @Component.
			registerDefaultFilters();   // 见父类ClassPathScanningCandidateComponentProvider代码3
		}
		setEnvironment(environment);        // 见父类ClassPathScanningCandidateComponentProvider代码5
		setResourceLoader(resourceLoader);  // AnnotationConfigApplicationContext
	}



 

	/**
        Perform a scan within the specified base packages, returning the registered bean definitions.
        This method does not register an annotation config processor but rather leaves this up to the caller.
        Params:
          basePackages – the packages to check for annotated classes
        Returns:
          set of beans registered if any for tooling registration purposes (never null)

		扫描指定的基包，返回BeanDefinition
	 */
    // 代码10
	protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
		
		for (String basePackage : basePackages) {
			// 遍历指定位置下的所有类
			Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
			
			for (BeanDefinition candidate : candidates) {
				ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
				candidate.setScope(scopeMetadata.getScopeName());
				String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
				if (candidate instanceof AbstractBeanDefinition) {
					postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);
				}
				if (candidate instanceof AnnotatedBeanDefinition) {
					AnnotationConfigUtils.processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);
				}
				if (checkCandidate(beanName, candidate)) {
					BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
					definitionHolder =
							AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
					beanDefinitions.add(definitionHolder);
					registerBeanDefinition(definitionHolder, this.registry);
				}
			}
		}
		return beanDefinitions;
	}
 
	/**
	 * 
		Scan the class path for candidate components.
		Params:
		  basePackage – the package to check for annotated classes
		Returns:
		  a corresponding Set of autodetected bean definitions
	 */
    // 代码11
	public Set<BeanDefinition> findCandidateComponents(String basePackage) {
		if (this.componentsIndex != null && indexSupportsIncludeFilters()) {
			return addCandidateComponentsFromIndex(this.componentsIndex, basePackage);
		}
		else {
			return scanCandidateComponents(basePackage);
		}
	}


    // 代码15
	private Set<BeanDefinition> scanCandidateComponents(String basePackage) {
		Set<BeanDefinition> candidates = new LinkedHashSet<>();
		try {
			// basePackage转成成classpath路径
			String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
					resolveBasePackage(basePackage) + '/' + this.resourcePattern;
			// 读取该路径下的资源		
			Resource[] resources = getResourcePatternResolver().getResources(packageSearchPath);
			boolean traceEnabled = logger.isTraceEnabled();
			boolean debugEnabled = logger.isDebugEnabled();
			for (Resource resource : resources) {
				if (traceEnabled) {
					logger.trace("Scanning " + resource);
				}
				try {
					MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(resource);
					if (isCandidateComponent(metadataReader)) {
						// 为每个组件类创建对应的 ScannedGenericBeanDefinition
						ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
						sbd.setSource(resource);
						if (isCandidateComponent(sbd)) {
							if (debugEnabled) {
								logger.debug("Identified candidate component class: " + resource);
							}
							candidates.add(sbd);
						}
						else {
							if (debugEnabled) {
								logger.debug("Ignored because not a concrete top-level class: " + resource);
							}
						}
					}
					else {
						if (traceEnabled) {
							logger.trace("Ignored because not matching any filter: " + resource);
						}
					}
				}
				catch (FileNotFoundException ex) {
					if (traceEnabled) {
						logger.trace("Ignored non-readable " + resource + ": " + ex.getMessage());
					}
				}
				catch (Throwable ex) {
					throw new BeanDefinitionStoreException(
							"Failed to read candidate component class: " + resource, ex);
				}
			}
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException("I/O failure during classpath scanning", ex);
		}
		return candidates;
	}



}
