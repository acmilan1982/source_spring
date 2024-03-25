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

 package org.springframework.beans;

 import java.lang.reflect.Field;
 import java.util.HashMap;
 import java.util.Map;

import org.springframework.beans.AbstractNestablePropertyAccessor;
import org.springframework.core.ResolvableType;
 import org.springframework.core.convert.TypeDescriptor;
 import org.springframework.lang.Nullable;
 import org.springframework.util.ReflectionUtils;
 
 /**
  * {@link ConfigurablePropertyAccessor} implementation that directly accesses
  * instance fields. Allows for direct binding to fields instead of going through
  * JavaBean setters.
  *
  * <p>As of Spring 4.2, the vast majority of the {@link BeanWrapper} features have
  * been merged to {@link AbstractPropertyAccessor}, which means that property
  * traversal as well as collections and map access is now supported here as well.
  *
  * <p>A DirectFieldAccessor's default for the "extractOldValueForEditor" setting
  * is "true", since a field can always be read without side effects.
  *
  * @author Juergen Hoeller
  * @author Stephane Nicoll
  * @since 2.0
  * @see #setExtractOldValueForEditor
  * @see BeanWrapper
  * @see org.springframework.validation.DirectFieldBindingResult
  * @see org.springframework.validation.DataBinder#initDirectFieldAccess()
  */
 public class DirectFieldAccessor extends AbstractNestablePropertyAccessor {
  
 
     private class FieldPropertyHandler extends PropertyHandler {
 
         private final Field field;
 
         public FieldPropertyHandler(Field field) {
             super(field.getType(), true, true);
             this.field = field;
         }
 
         @Override
         public TypeDescriptor toTypeDescriptor() {
             return new TypeDescriptor(this.field);
         }
 
         @Override
         public ResolvableType getResolvableType() {
             return ResolvableType.forField(this.field);
         }
 
         @Override
         @Nullable
         public TypeDescriptor nested(int level) {
             return TypeDescriptor.nested(this.field, level);
         }
 
         @Override
         @Nullable
         public Object getValue() throws Exception {
             try {
                 ReflectionUtils.makeAccessible(this.field);
                 return this.field.get(getWrappedInstance());
             }
 
             catch (IllegalAccessException ex) {
                 throw new InvalidPropertyException(getWrappedClass(),
                         this.field.getName(), "Field is not accessible", ex);
             }
         }
 
         @Override
         public void setValue(@Nullable Object value) throws Exception {
             try {
                 ReflectionUtils.makeAccessible(this.field);
                 this.field.set(getWrappedInstance(), value);
             }
             catch (IllegalAccessException ex) {
                 throw new InvalidPropertyException(getWrappedClass(), this.field.getName(),
                         "Field is not accessible", ex);
             }
         }
     }
 
 }
 