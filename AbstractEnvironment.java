/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.core.env;

import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.omg.CORBA.Environment;
import org.springframework.core.SpringProperties;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for {@link Environment} implementations. Supports the notion of
 * reserved default profile names and enables specifying active and default profiles
 * through the {@link #ACTIVE_PROFILES_PROPERTY_NAME} and
 * {@link #DEFAULT_PROFILES_PROPERTY_NAME} properties.
 *
 * <p>Concrete subclasses differ primarily on which {@link PropertySource} objects they
 * add by default. {@code AbstractEnvironment} adds none. Subclasses should contribute
 * property sources through the protected {@link #customizePropertySources(MutablePropertySources)}
 * hook, while clients should customize using {@link ConfigurableEnvironment#getPropertySources()}
 * and working against the {@link MutablePropertySources} API.
 * See {@link ConfigurableEnvironment} javadoc for usage examples.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.1
 * @see ConfigurableEnvironment
 * @see StandardEnvironment
 */
public abstract class AbstractEnvironment implements ConfigurableEnvironment {

	/**
	 * System property that instructs Spring to ignore system environment variables,
	 * i.e. to never attempt to retrieve such a variable via {@link System#getenv()}.
	 * <p>The default is "false", falling back to system environment variable checks if a
	 * Spring environment property (e.g. a placeholder in a configuration String) isn't
	 * resolvable otherwise. Consider switching this flag to "true" if you experience
	 * log warnings from {@code getenv} calls coming from Spring, e.g. on WebSphere
	 * with strict SecurityManager settings and AccessControlExceptions warnings.
	 * @see #suppressGetenvAccess()
	 */
	public static final String IGNORE_GETENV_PROPERTY_NAME = "spring.getenv.ignore";

	/**
	 * Name of property to set to specify active profiles: {@value}. Value may be comma
	 * delimited.
	 * <p>Note that certain shell environments such as Bash disallow the use of the period
	 * character in variable names. Assuming that Spring's {@link SystemEnvironmentPropertySource}
	 * is in use, this property may be specified as an environment variable as
	 * {@code SPRING_PROFILES_ACTIVE}.
	 * @see ConfigurableEnvironment#setActiveProfiles
	 */
	public static final String ACTIVE_PROFILES_PROPERTY_NAME = "spring.profiles.active";

	/**
	 * Name of property to set to specify profiles active by default: {@value}. Value may
	 * be comma delimited.
	 * <p>Note that certain shell environments such as Bash disallow the use of the period
	 * character in variable names. Assuming that Spring's {@link SystemEnvironmentPropertySource}
	 * is in use, this property may be specified as an environment variable as
	 * {@code SPRING_PROFILES_DEFAULT}.
	 * @see ConfigurableEnvironment#setDefaultProfiles
	 */
	public static final String DEFAULT_PROFILES_PROPERTY_NAME = "spring.profiles.default";

	/**
	 * Name of reserved default profile name: {@value}. If no default profile names are
	 * explicitly and no active profile names are explicitly set, this profile will
	 * automatically be activated by default.
	 * @see #getReservedDefaultProfiles
	 * @see ConfigurableEnvironment#setDefaultProfiles
	 * @see ConfigurableEnvironment#setActiveProfiles
	 * @see AbstractEnvironment#DEFAULT_PROFILES_PROPERTY_NAME
	 * @see AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 */
	protected static final String RESERVED_DEFAULT_PROFILE_NAME = "default";


	protected final Log logger = LogFactory.getLog(getClass());

	private final Set<String> activeProfiles = new LinkedHashSet<>();

	private final Set<String> defaultProfiles = new LinkedHashSet<>(getReservedDefaultProfiles());


	/**
	 *   PropertiesPropertySource
	 *   SystemEnvironmentPropertySource
	 * 
	 * 
	 * 
	 *  MutablePropertySources
	 * 
	 */	
	private final MutablePropertySources propertySources;

	/**
	 *   PropertySourcesPropertyResolver对象，其持有一个MutablePropertySources
	 * 
	 */
	private final ConfigurablePropertyResolver propertyResolver;


	/**
		Create a new Environment instance, 
		calling back to customizePropertySources(MutablePropertySources) during construction to allow subclasses to contribute or manipulate PropertySource instances as appropriate.
		See Also:
		  customizePropertySources(MutablePropertySources)
	 */
    // 构造方法1
	public AbstractEnvironment() {

		this(new MutablePropertySources());             // 见代码2
	} 

	/**
		Create a new Environment instance with a specific MutablePropertySources instance, 
		calling back to customizePropertySources(MutablePropertySources) during construction to allow subclasses to contribute or manipulate PropertySource instances as appropriate.
		
		Params:
		  propertySources – property sources to use
		Since:
		  5.3.4
		See Also:
		  customizePropertySources(MutablePropertySources)
	 */
    // 构造方法2
	protected AbstractEnvironment(MutablePropertySources propertySources) {
		// 创建 MutablePropertySources
		this.propertySources = propertySources;
		// 创建 ConfigurablePropertyResolver
		this.propertyResolver = createPropertyResolver(propertySources);        // 见代码2

		// 通常是向 MutablePropertySources 提供多个 PropertySource
		customizePropertySources(propertySources);  // 该方法由子类实现，见 StandardEnvironment 代码1
	}


	/**
		Factory method used to create the ConfigurablePropertyResolver instance used by the Environment.

		Since:
		  5.3.4
		See Also:
		  getPropertyResolver()
	 */
    // 代码2
	protected ConfigurablePropertyResolver createPropertyResolver(MutablePropertySources propertySources) {
		return new PropertySourcesPropertyResolver(propertySources);
	}



	/**
		Description copied from interface: ConfigurableEnvironment

		Return the value of System.getProperties() if allowed by the current SecurityManager, 
		otherwise return a map implementation that will attempt to access individual keys using calls to System.getProperty(String).
		
		Note that most Environment implementations will include this system properties map as a default PropertySource to be searched. 
		Therefore, it is recommended that this method not be used directly unless bypassing other property sources is expressly intended.
		
		Calls to Map.get(Object) on the Map returned will never throw IllegalAccessException; 
		in cases where the SecurityManager forbids access to a property, 
		null will be returned and an INFO-level log message will be issued noting the exception.
		Specified by:
		getSystemProperties in interface ConfigurableEnvironment
	 */
    // 代码3
	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public Map<String, Object> getSystemProperties() {
		try {
			return (Map) System.getProperties();
		}
		catch (AccessControlException ex) {
			return (Map) new ReadOnlySystemAttributesMap() {
				@Override
				@Nullable
				protected String getSystemAttribute(String attributeName) {
					try {
						return System.getProperty(attributeName);
					}
					catch (AccessControlException ex) {
						if (logger.isInfoEnabled()) {
							logger.info("Caught AccessControlException when accessing system property '" +
									attributeName + "'; its value will be returned [null]. Reason: " + ex.getMessage());
						}
						return null;
					}
				}
			};
		}
	}


	/**
		Description copied from interface: ConfigurableEnvironment
		Return the value of System.getenv() if allowed by the current SecurityManager, 
		otherwise return a map implementation that will attempt to access individual keys using calls to System.getenv(String).
		
		Note that most Environment implementations will include this system environment map as a default PropertySource to be searched. 
		Therefore, it is recommended that this method not be used directly unless bypassing other property sources is expressly intended.
		
		Calls to Map.get(Object) on the Map returned will never throw IllegalAccessException; 
		in cases where the SecurityManager forbids access to a property, null will be returned and an INFO-level log message will be issued noting the exception.
	 */
    // 代码4
	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public Map<String, Object> getSystemEnvironment() {
		if (suppressGetenvAccess()) {
			return Collections.emptyMap();
		}
		try {
			return (Map) System.getenv();
		}
		catch (AccessControlException ex) {
			return (Map) new ReadOnlySystemAttributesMap() {
				@Override
				@Nullable
				protected String getSystemAttribute(String attributeName) {
					try {
						return System.getenv(attributeName);
					}
					catch (AccessControlException ex) {
						if (logger.isInfoEnabled()) {
							logger.info("Caught AccessControlException when accessing system environment variable '" +
									attributeName + "'; its value will be returned [null]. Reason: " + ex.getMessage());
						}
						return null;
					}
				}
			};
		}
	}












	/**
  
	 */
    // 代码10	
	@Override
	public String resolvePlaceholders(String text) {
		// PropertySourcesPropertyResolver对象，其持有一个MutablePropertySources
		return this.propertyResolver.resolvePlaceholders(text);   // 见 PropertySourcesPropertyResolver 的 父类	AbstractPropertyResolver 代码5
	}
 





}
