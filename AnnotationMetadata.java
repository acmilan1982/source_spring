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

package org.springframework.core.type;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

/**
    Interface that defines abstract access to the annotations of a specific class, 
    in a form that does not require that class to be loaded yet.

    Since:
      2.5
    See Also:
      StandardAnnotationMetadata, org.springframework.core.type.classreading.MetadataReader.getAnnotationMetadata(), AnnotatedTypeMetadata
    Author:
      Juergen Hoeller, Mark Fisher, Phillip Webb, Sam Brannen

    定义了访问指定类型(类/方法)注解的方式，从某种形式来说，可以不加载类，就读到注解 
 */
public interface AnnotationMetadata extends ClassMetadata, AnnotatedTypeMetadata {

 

    /**
          Factory method to create a new AnnotationMetadata instance for the given class using standard reflection.
          
          Params:
            type – the class to introspect
          Returns:
            a new AnnotationMetadata instance
          Since:
          5.2

          创建一个StandardAnnotationMetadata示例
    */
    // 代码10
    static AnnotationMetadata introspect(Class<?> type) {
      // 创建StandardAnnotationMetadata实例
      return StandardAnnotationMetadata.from(type);    // 见StandardAnnotationMetadata代码10
    }

}
