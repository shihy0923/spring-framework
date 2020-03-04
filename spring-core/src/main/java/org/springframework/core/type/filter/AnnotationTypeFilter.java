/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.core.type.filter;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;

/**
 * A simple filter which matches classes with a given annotation,
 * checking inherited annotations as well.
 *
 * <p>The matching logic mirrors that of {@link java.lang.Class#isAnnotationPresent(Class)}.
 *
 * @author Mark Fisher
 * @author Ramnivas Laddad
 * @author Juergen Hoeller
 * @since 2.5
 */
public class AnnotationTypeFilter extends AbstractTypeHierarchyTraversingFilter {

	private final Class<? extends Annotation> annotationType;

	private final boolean considerMetaAnnotations;


	/**
	 * Create a new AnnotationTypeFilter for the given annotation type.
	 * This filter will also match meta-annotations. To disable the
	 * meta-annotation matching, use the constructor that accepts a
	 * '{@code considerMetaAnnotations}' argument. The filter will
	 * not match interfaces.
	 * @param annotationType the annotation type to match
	 */
	public AnnotationTypeFilter(Class<? extends Annotation> annotationType) {
		this(annotationType, true, false);
	}

	/**
	 * Create a new AnnotationTypeFilter for the given annotation type.
	 * The filter will not match interfaces.
	 * @param annotationType the annotation type to match
	 * @param considerMetaAnnotations whether to also match on meta-annotations
	 */
	public AnnotationTypeFilter(Class<? extends Annotation> annotationType, boolean considerMetaAnnotations) {
		this(annotationType, considerMetaAnnotations, false);
	}

	/**
	 * Create a new {@link AnnotationTypeFilter} for the given annotation type.
	 * @param annotationType the annotation type to match
	 * @param considerMetaAnnotations whether to also match on meta-annotations
	 * @param considerInterfaces whether to also match interfaces
	 */
	public AnnotationTypeFilter(
			Class<? extends Annotation> annotationType, boolean considerMetaAnnotations, boolean considerInterfaces) {

		super(annotationType.isAnnotationPresent(Inherited.class), considerInterfaces);
		//创建容器的时候就会给annotationType为默认值Component.class。
		this.annotationType = annotationType;
		//创建容器的时候就会给considerMetaAnnotations为默认值true。
		this.considerMetaAnnotations = considerMetaAnnotations;
	}

	/*在这段代码代码中，可以解决我们之前的疑惑“Spring是怎么发现@Configuration、@Controller、@Service这些注解修饰的类的？”
	原来@Configuration、@Controller、@Service这些注解其实都是@Component的派生注解，我们看这些注解的代码会发现，都有@Component注解修饰。而spring通过metadata.hasMetaAnnotation()方法获取到这些注解包含@Component，所以都可以扫描到*/
	@Override
	protected boolean matchSelf(MetadataReader metadataReader) {
		//获取注解元数据，包含两个重要属性annotationSet(放类的注解)和metaAnnotationMap(放类的注解的注解)
		AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();
		//check 注解及其注解的注解中是否包含@Component
		//获取当前类的注解是否包含@Component    metadata.hasAnnotation    如：@Controller
		//获取当前类的注解的原生注解是否包含@Component    metadata.hasMetaAnnotation   @Controller包含的@Component
		return metadata.hasAnnotation(this.annotationType.getName()) ||
				(this.considerMetaAnnotations && metadata.hasMetaAnnotation(this.annotationType.getName()));//this.annotationType.getName())的值为org.springframework.stereotype.Component
	}

	@Override
	protected Boolean matchSuperClass(String superClassName) {
		return hasAnnotation(superClassName);
	}

	@Override
	protected Boolean matchInterface(String interfaceName) {
		return hasAnnotation(interfaceName);
	}

	protected Boolean hasAnnotation(String typeName) {
		if (Object.class.getName().equals(typeName)) {
			return false;
		}
		else if (typeName.startsWith("java")) {
			if (!this.annotationType.getName().startsWith("java")) {
				// Standard Java types do not have non-standard annotations on them ->
				// skip any load attempt, in particular for Java language interfaces.
				return false;
			}
			try {
				Class<?> clazz = ClassUtils.forName(typeName, getClass().getClassLoader());
				return ((this.considerMetaAnnotations ? AnnotationUtils.getAnnotation(clazz, this.annotationType) :
						clazz.getAnnotation(this.annotationType)) != null);
			}
			catch (Throwable ex) {
				// Class not regularly loadable - can't determine a match that way.
			}
		}
		return null;
	}

}
