

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
 
	/**
	 * A collection of {@link MirrorSet} instances that provides details of all
	 * defined mirrors.
	 */
	class MirrorSets {

        // 每个属性，对应个MirrorSet
		private MirrorSet[] mirrorSets;

		private final MirrorSet[] assigned;

		MirrorSets() {
			// 创建 属性个数 大小的数组，注意，此时数组元素为null
			this.assigned = new MirrorSet[attributes.size()];
			this.mirrorSets = EMPTY_MIRROR_SETS;
		}


		// 代码1
		void updateFrom(Collection<Method> aliases) {  // aliases:整个层级结构中当前属性相关的别名
			MirrorSet mirrorSet = null;
			int size = 0;
			int last = -1;

			// 遍历当前注解全部属性，当前别名属性的分组，在assigned的数组对应索引位置上创建一个MirrorSet
			for (int i = 0; i < attributes.size(); i++) {
				Method attribute = attributes.get(i);
				// 当前注解全部属性中，当前别名属性的分组
				if (aliases.contains(attribute)) {
					size++;
					// 有别名的属性，在assigned的对应索引位置上创建一个MirrorSet
					if (size > 1) {
						if (mirrorSet == null) {
							mirrorSet = new MirrorSet();         
							this.assigned[last] = mirrorSet;
						}
						this.assigned[i] = mirrorSet;
					}
					last = i;
				}
			}

			if (mirrorSet != null) {
				// mirrorSet中记录共享当前MirrorSet的属性索引
				mirrorSet.update();                                                            // 见 MirrorSet 代码1
				Set<MirrorSet> unique = new LinkedHashSet<>(Arrays.asList(this.assigned));
				unique.remove(null);
				this.mirrorSets = unique.toArray(EMPTY_MIRROR_SETS);
			}
		}

 




		// 代码10
		int[] resolve(@Nullable Object source, 
					  @Nullable Object annotation, 
					  ValueExtractor valueExtractor) {        // ReflectionUtils::invokeMethod
			int[] result = new int[attributes.size()];
			// 默认情况下，每个属性都调用他本身
			for (int i = 0; i < result.length; i++) {
				result[i] = i;
			}

			// 遍历所有MirrorSet
			for (int i = 0; i < size(); i++) {
				MirrorSet mirrorSet = get(i);
				// 如果有MirrorSet，则调用resolve方法获得这一组关联属性中的唯一有效属性的下标
				int resolved = mirrorSet.resolve(source, annotation, valueExtractor);
				 // 将该下标强制覆盖全部关联的属性
				for (int j = 0; j < mirrorSet.size; j++) {
					result[mirrorSet.indexes[j]] = resolved;
				}
			}
			return result;
		}


		MirrorSet get(int index) {
			return this.mirrorSets[index];
		}
	 
		int size() {
			return this.mirrorSets.length;
		}


		@Nullable
		MirrorSet getAssigned(int attributeIndex) {
			return this.assigned[attributeIndex];
		}


	}






}
