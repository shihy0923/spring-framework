/*
 * Copyright 2002-2013 the original author or authors.
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

import org.springframework.core.type.AnnotationMetadata;

/**
 * Interface to be implemented by types that determine which @{@link Configuration}
 * class(es) should be imported based on a given selection criteria, usually one or
 * more annotation attributes.
 *
 * <p>An {@link ImportSelector} may implement any of the following
 * {@link org.springframework.beans.factory.Aware Aware} interfaces,
 * and their respective methods will be called prior to {@link #selectImports}:
 * <ul>
 * <li>{@link org.springframework.context.EnvironmentAware EnvironmentAware}</li>
 * <li>{@link org.springframework.beans.factory.BeanFactoryAware BeanFactoryAware}</li>
 * <li>{@link org.springframework.beans.factory.BeanClassLoaderAware BeanClassLoaderAware}</li>
 * <li>{@link org.springframework.context.ResourceLoaderAware ResourceLoaderAware}</li>
 * </ul>
 *
 * <p>{@code ImportSelector} implementations are usually processed in the same way
 * as regular {@code @Import} annotations, however, it is also possible to defer
 * selection of imports until all {@code @Configuration} classes have been processed
 * (see {@link DeferredImportSelector} for details).
 *
 * @author Chris Beams
 * @since 3.1
 * @see DeferredImportSelector
 * @see Import
 * @see ImportBeanDefinitionRegistrar
 * @see Configuration
 */
public interface ImportSelector {

	/**
	 * Select and return the names of which class(es) should be imported based on
	 * the {@link AnnotationMetadata} of the importing @{@link Configuration} class.
	 */

	/*
	例子：https://www.cnblogs.com/heliusKing/p/11372014.html

	//自定义逻辑返回需要导入的组件
	public class MyImportSelector implements ImportSelector {

		//返回值，就是到导入的配置类的全类名。
		//AnnotationMetadata:当前标注@Import注解的类的所有注解信息
		@Override
		public String[] selectImports(AnnotationMetadata importingClassMetadata) {
			//方法不要返回null值
			return new String[]{"com.atguigu.bean.Blue","com.atguigu.bean.Yellow"};
		}
	}

	@Configuration
	@Import({Color.class,Red.class,MyImportSelector.class})//将这两个类导入到容器中
	public class MainConfig2 {
	}*/


	//返回值，就是到导入的配置类的全类名。实现该接口，实现该方法，该方法会在ConfigurationClassParser类的private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,Collection<SourceClass> importCandidates, boolean checkForCircularImports) 方法中的自动调用
	String[] selectImports(AnnotationMetadata importingClassMetadata);

}
