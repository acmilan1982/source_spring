

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

import org.springframework.core.annotation.AnnotationTypeMapping.MirrorSets.MirrorSet;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;


final class AnnotationTypeMapping {
 

	class MirrorSets {
 
		/**
		 * A single set of mirror attributes.
		 */
		class MirrorSet {

			private int size;

			// 共享当前MirrorSet的属性索引
			private final int[] indexes = new int[attributes.size()];

			// 代码1
			void update() {
				this.size = 0;
				Arrays.fill(this.indexes, -1);
				for (int i = 0; i < MirrorSets.this.assigned.length; i++) {
					if (MirrorSets.this.assigned[i] == this) {
						this.indexes[this.size] = i;
						this.size++;
					}
				}
			}

			// 代码10
			<A> int resolve(@Nullable Object source, 
							@Nullable A annotation, 
							ValueExtractor valueExtractor) { // ReflectionUtils::invokeMethod
				int result = -1;
				// 最近一个的有效属性值
				Object lastValue = null;

				// 遍历共享当前MirrorSet的属性
				for (int i = 0; i < this.size; i++) {	
					Method attribute = attributes.get(this.indexes[i]);

					// 获取当前属性值
					Object value = valueExtractor.extract(attribute, annotation);   

					// 如果属性值是默认值，或者与最后有效值相同，则记录该属性下标后返回
                    // 以此类推，如果一组互为别名的属性全部都是默认值，则前面的属性——即离根注解最近的——的默认值会作为最终有效值


					// 当前属性是否为默认值
					boolean isDefaultValue = (value == null ||
							isEquivalentToDefaultValue(attribute, value, valueExtractor));  // 判断属性的实际值，是否与默认值相同
							                                                                // 见外部类 AnnotationTypeMapping 代码25

					if (isDefaultValue || ObjectUtils.nullSafeEquals(lastValue, value)) {
						if (result == -1) {
							result = this.indexes[i];
						}
						continue;
					}

					// 如果属性值不是默认值，并且与最近一个的有效属性值不同, 则抛出异常
                    // 这里实际要求一组互为别名的属性中，只允许一个属性的值是非默认值
					if (lastValue != null && !ObjectUtils.nullSafeEquals(lastValue, value)) {
						String on = (source != null) ? " declared on " + source : "";
						throw new AnnotationConfigurationException(String.format(
								"Different @AliasFor mirror values for annotation [%s]%s; attribute '%s' " +
								"and its alias '%s' are declared with values of [%s] and [%s].",
								getAnnotationType().getName(), on,
								attributes.get(result).getName(),
								attribute.getName(),
								ObjectUtils.nullSafeToString(lastValue),
								ObjectUtils.nullSafeToString(value)));
					}

					
					result = this.indexes[i];
					lastValue = value;
				}
				return result;
			}

			int size() {
				return this.size;
			}

			Method get(int index) {
				int attributeIndex = this.indexes[index];
				return attributes.get(attributeIndex);
			}

			int getAttributeIndex(int index) {
				return this.indexes[index];
			}
		}

	}



}
