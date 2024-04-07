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

 package org.springframework.web.servlet.handler;

 import java.lang.reflect.Method;
 import java.util.ArrayList;
 import java.util.Arrays;
 import java.util.Collection;
 import java.util.Collections;
 import java.util.Comparator;
 import java.util.HashMap;
 import java.util.HashSet;
 import java.util.List;
 import java.util.Map;
 import java.util.Set;
 import java.util.concurrent.ConcurrentHashMap;
 import java.util.concurrent.locks.ReentrantReadWriteLock;
 import java.util.function.Function;
 import java.util.stream.Collectors;
 
 import javax.servlet.ServletException;
 import javax.servlet.http.HttpServletRequest;
 
 import org.springframework.aop.support.AopUtils;
 import org.springframework.beans.factory.BeanFactoryUtils;
 import org.springframework.beans.factory.InitializingBean;
 import org.springframework.core.MethodIntrospector;
 import org.springframework.lang.Nullable;
 import org.springframework.util.Assert;
 import org.springframework.util.ClassUtils;
 import org.springframework.util.LinkedMultiValueMap;
 import org.springframework.util.MultiValueMap;
 import org.springframework.util.StringUtils;
 import org.springframework.web.cors.CorsConfiguration;
 import org.springframework.web.cors.CorsUtils;
 import org.springframework.web.method.HandlerMethod;
 import org.springframework.web.servlet.HandlerMapping;
 import org.springframework.web.util.pattern.PathPatternParser;
 
 /**
  * Abstract base class for {@link HandlerMapping} implementations that define
  * a mapping between a request and a {@link HandlerMethod}.
  *
  * <p>For each registered handler method, a unique mapping is maintained with
  * subclasses defining the details of the mapping type {@code <T>}.
  *
  * @author Arjen Poutsma
  * @author Rossen Stoyanchev
  * @author Juergen Hoeller
  * @author Sam Brannen
  * @since 3.1
  * @param <T> the mapping for a {@link HandlerMethod} containing the conditions
  * needed to match the handler method to an incoming request.
  */
 public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping implements InitializingBean {
 
     
 
     /**
        A registry that maintains all mappings to handler methods, exposing methods to perform lookups and providing concurrent access.
        Package-private for testing purposes.
      */
     class MappingRegistry {
 
         private final Map<T, MappingRegistration<T>> registry = new HashMap<>();
 
         private final MultiValueMap<String, T> pathLookup = new LinkedMultiValueMap<>();
 
         private final Map<String, List<HandlerMethod>> nameLookup = new ConcurrentHashMap<>();
 
         private final Map<HandlerMethod, CorsConfiguration> corsLookup = new ConcurrentHashMap<>();
 
         private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
 
         /**
          * Return all registrations.
          * @since 5.3
          */
         public Map<T, MappingRegistration<T>> getRegistrations() {
             return this.registry;
         }
 
         /**
          * Return matches for the given URL path. Not thread-safe.
          * @see #acquireReadLock()
          */
         @Nullable
         public List<T> getMappingsByDirectPath(String urlPath) {
             return this.pathLookup.get(urlPath);
         }
 
         /**
          * Return handler methods by mapping name. Thread-safe for concurrent use.
          */
         public List<HandlerMethod> getHandlerMethodsByMappingName(String mappingName) {
             return this.nameLookup.get(mappingName);
         }
 
         /**
          * Return CORS configuration. Thread-safe for concurrent use.
          */
         @Nullable
         public CorsConfiguration getCorsConfiguration(HandlerMethod handlerMethod) {
             HandlerMethod original = handlerMethod.getResolvedFromHandlerMethod();
             return this.corsLookup.get(original != null ? original : handlerMethod);
         }
 
         /**
          * Acquire the read lock when using getMappings and getMappingsByUrl.
          */
         public void acquireReadLock() {
             this.readWriteLock.readLock().lock();
         }
 
         /**
          * Release the read lock after using getMappings and getMappingsByUrl.
          */
         public void releaseReadLock() {
             this.readWriteLock.readLock().unlock();
         }
 
         public void register(T mapping, Object handler, Method method) {
             this.readWriteLock.writeLock().lock();
             try {
                 HandlerMethod handlerMethod = createHandlerMethod(handler, method);
                 validateMethodMapping(handlerMethod, mapping);
 
                 Set<String> directPaths = AbstractHandlerMethodMapping.this.getDirectPaths(mapping);
                 for (String path : directPaths) {
                     this.pathLookup.add(path, mapping);
                 }
 
                 String name = null;
                 if (getNamingStrategy() != null) {
                     name = getNamingStrategy().getName(handlerMethod, mapping);
                     addMappingName(name, handlerMethod);
                 }
 
                 CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
                 if (corsConfig != null) {
                     corsConfig.validateAllowCredentials();
                     this.corsLookup.put(handlerMethod, corsConfig);
                 }
 
                 this.registry.put(mapping,
                         new MappingRegistration<>(mapping, handlerMethod, directPaths, name, corsConfig != null));
             }
             finally {
                 this.readWriteLock.writeLock().unlock();
             }
         }
 
         private void validateMethodMapping(HandlerMethod handlerMethod, T mapping) {
             MappingRegistration<T> registration = this.registry.get(mapping);
             HandlerMethod existingHandlerMethod = (registration != null ? registration.getHandlerMethod() : null);
             if (existingHandlerMethod != null && !existingHandlerMethod.equals(handlerMethod)) {
                 throw new IllegalStateException(
                         "Ambiguous mapping. Cannot map '" + handlerMethod.getBean() + "' method \n" +
                         handlerMethod + "\nto " + mapping + ": There is already '" +
                         existingHandlerMethod.getBean() + "' bean method\n" + existingHandlerMethod + " mapped.");
             }
         }
 
         private void addMappingName(String name, HandlerMethod handlerMethod) {
             List<HandlerMethod> oldList = this.nameLookup.get(name);
             if (oldList == null) {
                 oldList = Collections.emptyList();
             }
 
             for (HandlerMethod current : oldList) {
                 if (handlerMethod.equals(current)) {
                     return;
                 }
             }
 
             List<HandlerMethod> newList = new ArrayList<>(oldList.size() + 1);
             newList.addAll(oldList);
             newList.add(handlerMethod);
             this.nameLookup.put(name, newList);
         }
 
         public void unregister(T mapping) {
             this.readWriteLock.writeLock().lock();
             try {
                 MappingRegistration<T> registration = this.registry.remove(mapping);
                 if (registration == null) {
                     return;
                 }
 
                 for (String path : registration.getDirectPaths()) {
                     List<T> mappings = this.pathLookup.get(path);
                     if (mappings != null) {
                         mappings.remove(registration.getMapping());
                         if (mappings.isEmpty()) {
                             this.pathLookup.remove(path);
                         }
                     }
                 }
 
                 removeMappingName(registration);
 
                 this.corsLookup.remove(registration.getHandlerMethod());
             }
             finally {
                 this.readWriteLock.writeLock().unlock();
             }
         }
 
         private void removeMappingName(MappingRegistration<T> definition) {
             String name = definition.getMappingName();
             if (name == null) {
                 return;
             }
             HandlerMethod handlerMethod = definition.getHandlerMethod();
             List<HandlerMethod> oldList = this.nameLookup.get(name);
             if (oldList == null) {
                 return;
             }
             if (oldList.size() <= 1) {
                 this.nameLookup.remove(name);
                 return;
             }
             List<HandlerMethod> newList = new ArrayList<>(oldList.size() - 1);
             for (HandlerMethod current : oldList) {
                 if (!current.equals(handlerMethod)) {
                     newList.add(current);
                 }
             }
             this.nameLookup.put(name, newList);
         }
     }
 
  
 
 }
 