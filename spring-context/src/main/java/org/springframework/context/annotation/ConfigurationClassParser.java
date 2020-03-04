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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.NestedIOException;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.*;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 3.0
 * @see ConfigurationClassBeanDefinitionReader
 */
class ConfigurationClassParser {

	private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

	private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR =
			new Comparator<ConfigurationClassParser.DeferredImportSelectorHolder>() {
				@Override
				public int compare(DeferredImportSelectorHolder o1, DeferredImportSelectorHolder o2) {
					return AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());
				}
			};


	private final Log logger = LogFactory.getLog(getClass());

	private final MetadataReaderFactory metadataReaderFactory;

	private final ProblemReporter problemReporter;

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	private final BeanDefinitionRegistry registry;

	private final ComponentScanAnnotationParser componentScanParser;

	private final ConditionEvaluator conditionEvaluator;
	//configurationClasses属性里面放置的都是@Configuration类对应的ConfigurationClass对象或者是通过@Import引入的类对应的ConfigurationClass对象,并没有真正的解析里面的@Bean为一个BeanDefinition，而是将@Bean解析为BeanMethod对象。configurationClasses属性的值ConfigurationClass对象后续会被一个个取出来解析里面的BeanMethod对象再解析为一个个BeanDefinition.如果加了@Configuration，那么对应的BeanDefinition为full;
	//					// 如果加了@Bean,@Component,@ComponentScan,@Import,@ImportResource这些注解，则为lite。
	private final Map<ConfigurationClass, ConfigurationClass> configurationClasses =
			new LinkedHashMap<ConfigurationClass, ConfigurationClass>();

	private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<String, ConfigurationClass>();

	private final List<String> propertySourceNames = new ArrayList<String>();

	private final ImportStack importStack = new ImportStack();

	private List<DeferredImportSelectorHolder> deferredImportSelectors;


	/**
	 * Create a new {@link ConfigurationClassParser} instance that will be used
	 * to populate the set of configuration classes.
	 */
	public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory,
			ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader,
			BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

		this.metadataReaderFactory = metadataReaderFactory;
		this.problemReporter = problemReporter;
		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.registry = registry;
		this.componentScanParser = new ComponentScanAnnotationParser(
				environment, resourceLoader, componentScanBeanNameGenerator, registry);
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		this.deferredImportSelectors = new LinkedList<DeferredImportSelectorHolder>();

		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition();
			try {
				//注解形式的 bean 定义信息
				if (bd instanceof AnnotatedBeanDefinition) {
					//解析@Configuration类的BeanDefinition
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				}
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				}
				else {
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}
		//处理延迟importSelector--DeferredImportSelector
		// DeferredImportSelector是ImportSelector 的一个变种。
		// ImportSelector 被设计成其实和@Import注解的类同样的导入效果，但是实现 ImportSelector
		// 的类可以条件性地决定导入哪些配置。
		// DeferredImportSelector的设计目的是在所有其他的配置类被处理后才处理。这也正是
		// 该语句被放到本函数最后一行的原因。
		processDeferredImportSelectors();
	}

	protected final void parse(String className, String beanName) throws IOException {
		MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
		processConfigurationClass(new ConfigurationClass(reader, beanName));
	}

	protected final void parse(Class<?> clazz, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(clazz, beanName));
	}

	protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(metadata, beanName));
	}

	/**
	 * Validate each {@link ConfigurationClass} object.
	 * @see ConfigurationClass#validate
	 */
	public void validate() {
		for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
			configClass.validate(this.problemReporter);
		}
	}

	public Set<ConfigurationClass> getConfigurationClasses() {
		return this.configurationClasses.keySet();
	}

    //该方法会递归调用
	protected void processConfigurationClass(ConfigurationClass configClass) throws IOException {
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			return;
		}
        //用来处理一个@Configuration类或者@Import引入的类重复解析为ConfigurationClass对象的问题
		// 本质是根据计算ConfigurationClass对象的全限定类名(一个String类型的字符串，调用String的hashCode())计算出的hascode值从configurationClasses(一个Map，按照全限定名--)里面看是否已经存在。
		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		//表示该ConfigurationClass对象在之前的步骤中已经处理过并放入了configurationClasses
		if (existingClass != null) {
			//如果当前ConfigurationClass对象是通过@Import引入的
			if (configClass.isImported()) {
				//如果已存在的ConfigurationClass对象也是通过@Import引入的
				if (existingClass.isImported()) {
					//将两个对象进行合并，以已存在的ConfigurationClass对象为基准，将两个对象的importedBy属性进行合并。
					existingClass.mergeImportedBy(configClass);
				}
				// Otherwise ignore new imported config class; existing non-imported class overrides it.
				//如果已存在的ConfigurationClass对象不是通过@Import引入的，则忽略当前@Import引入的ConfigurationClass对象，以已存在的ConfigurationClass对象为准
				return;
			}
			else {
				// Explicit bean definition found, probably replacing an import.
				// Let's remove the old one and go with the new one.
				//如果当前ConfigurationClass对象不是通过@Import引入的，则删除已存在的ConfigurationClass对象。在下面的this.configurationClasses.put(configClass, configClass);代码中会将前ConfigurationClass对象重新放入configurationClasses中。
				this.configurationClasses.remove(configClass);
				for (Iterator<ConfigurationClass> it = this.knownSuperclasses.values().iterator(); it.hasNext();) {
					if (configClass.equals(it.next())) {
						it.remove();
					}
				}
			}
		}

		// Recursively process the configuration class and its superclass hierarchy.
		// 处理配置类，由于配置类可能存在父类(若父类的全类名是以java开头的，则除外)，所有需要将configClass变成sourceClass去解析，然后返回sourceClass的父类。
		// 如果此时父类为空，则不会进行while循环去解析，如果父类不为空，则会循环的去解析父类
		// SourceClass的意义：简单的包装类，目的是为了以统一的方式去处理带有注解的类，不管这些类是如何加载的
		// 如果无法理解，可以把它当做一个黑盒，不会影响看spring源码的主流程
		SourceClass sourceClass = asSourceClass(configClass);
		do {
            //这个方法只会处理标注在类上面的注解。是真正做事情的方法
			sourceClass = doProcessConfigurationClass(configClass, sourceClass);
		}
		while (sourceClass != null);
        //configurationClasses属性里面放置的都是@Configuration类对应的ConfigurationClass对象或者是通过@Import引入的类对应的ConfigurationClass对象,并没有真正的解析里面的@Bean为一个BeanDefinition，而是将@Bean解析为BeanMethod对象。configurationClasses属性的值ConfigurationClass对象后续会被一个个取出来解析里面的BeanMethod对象再解析为一个个BeanDefinition
		this.configurationClasses.put(configClass, configClass);
	}

	/**
	 * Apply processing and build a complete {@link ConfigurationClass} by reading the
	 * annotations, members and methods from the source class. This method can be called
	 * multiple times as relevant sources are discovered.
	 * @param configClass the configuration class being build
	 * @param sourceClass a source class
	 * @return the superclass, or {@code null} if none found or previously processed
	 */
	//这个方法只会处理标注在@Configuration类（以及通过@Import导入的类--第1种和第3种）上面的注解
	protected final SourceClass doProcessConfigurationClass(ConfigurationClass configClass, SourceClass sourceClass)
			throws IOException {

		// Recursively process any member (nested) classes first
		// 处理内部类
		processMemberClasses(configClass, sourceClass);

		// Process any @PropertySource annotations
		//处理@PropertySource 注解
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			if (this.environment instanceof ConfigurableEnvironment) {
				processPropertySource(propertySource);
			}
			else {
				logger.warn("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}

		// Process any @ComponentScan annotations
		//1. 处理@ComponentScan 注解
        //解析@ComponentScans 注解的属性 封装成一个一个的 componentscan 对象
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			//循环 componentScans 的 set
			for (AnnotationAttributes componentScan : componentScans) {
				// The config class is annotated with @ComponentScan -> perform the scan immediately
				// 处理@ComponentScan 注解，根据配置类（标有@Configuration注解的类）中配置的@ComponentScan的basePackages(basePackages是最主要的属性，当然还有其他的属性，暂不考虑)的值扫描出来BeanDefinition(扫描出来的主要是标注了@Component那一派注解的类)，并注册到容器中（若扫描出来的BeanDefinition对应的类，定义了@Bean，暂不处理这个@Bean）。然后放入scannedBeanDefinitions。
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
				//遍历扫描到的BeanDefinition，是否有@Configuration类
				// Check the set of scanned definitions for any further config classes and parse recursively if needed
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					//获取BeanDefinition对象
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
					// checkConfigurationClassCandidate()会判断BeanDefinition是否是一个配置类,并为BeanDefinition设置属性为lite或者full。
					// 在这儿为BeanDefinition设置lite和full属性值是为了后面在使用
					// 如果加了@Configuration，那么对应的BeanDefinition为full;
					// 如果加了@Bean,@Component,@ComponentScan,@Import,@ImportResource这些注解，则为lite。
					//lite和full均表示这个BeanDefinition对应的类是一个配置类
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						//若是配置类，又用到了递归，递归调用来解析该配置类。
						parse(bdCand.getBeanClassName(), holder.getBeanName());//从该处开始-->processConfigurationClass(new ConfigurationClass(reader, beanName))-->又回到当前方法doProcessConfigurationClass(configClass, sourceClass)
					}
				}
			}
		}
		//2. 处理@Import注解,对注解的引入的三种类分别做处理。
		//2.1 对于引入ImportSelector类型的类，递归调用processImports方法，最终将ImportSelector类对象的selectImports方法返回的值，解析成ConfigurationClass对象放到了ConfigurationClassParser.class的configurationClasses这个Map属性中，作为待解析。对于它的子接口DeferredImportSelector，只是会延时处理，其他逻辑一样的，同样会调用DeferredImportSelector对象的selectImports方法，并将返回的值，解析成ConfigurationClass对象放到了ConfigurationClassParser.class的configurationClasses这个Map属性中，作为待解析。
		//2.2 对于引入ImportBeanDefinitionRegistrar类型的类，把ImportBeanDefinitionRegistrar对象放入当前的ConfigurationClass对象，在ConfigurationClassBeanDefinitionReader.class的loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars())会将ImportBeanDefinitionRegistrar对象取出来调用它的registerBeanDefinitions方法，进行手动注册额外的BeanDefinition。
		// 2.3 对于其他类型，都当作配置类处理，也就是相当于使用了注解@Configuration的配置类。在这里并没有将@Import进来的配置类解析成Beanfition，而是解析成了ConfigurationClass对象放到了ConfigurationClassParser.class的configurationClasses这个Map属性中，作为待解析。
		//总结：在这步处理完所有的@Import注解后，并不会把上面的三种类解析为BeanDefinition，而是会把2.1和2.3引入的类，解析成一个ConfigurationClass对象放到了ConfigurationClassParser.class的configurationClasses这个Map属性中，继而在ConfigurationClassPostProcessor.class的this.reader.loadBeanDefinitions(configClasses);中处理各个ConfigurationClass对象。2.2的ImportBeanDefinitionRegistrar对象放入当前的ConfigurationClass对象，也会在this.reader.loadBeanDefinitions(configClasses);的调用链中被处理。
		// Process any @Import annotations
		processImports(configClass, sourceClass, getImports(sourceClass), true);//从该处开始-->processConfigurationClass(candidate.asConfigClass(configClass))-->又回到当前方法doProcessConfigurationClass(configClass, sourceClass)。和上面parse(bdCand.getBeanClassName(), holder.getBeanName())流程一样。
		//处理 @ImportResource annotations
		// Process any @ImportResource annotations
		if (sourceClass.getMetadata().isAnnotated(ImportResource.class.getName())) {
			AnnotationAttributes importResource =
					AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
			String[] resources = importResource.getStringArray("locations");
			//遍历配置的locations,加入到configClass 中的ImportedResource
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}
         //3. 处理@Configuration类（以及通过@Import导入的类--第1种和第3种）中每个带有@Bean注解的方法，获取方法的元数据
		// Process individual @Bean methods
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		for (MethodMetadata methodMetadata : beanMethods) {
			//将方法的元数据解析成一个个BeanMethod对象，并将这个BeanMethod对象放入到它们对应的ConfigurationClass对象中去，		该ConfigurationClass对象会放到configurationClasses属性中
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}
		//4.处理接口
		// Process default methods on interfaces
		processInterfaces(configClass, sourceClass);

		//5. 处理父类
		// Process superclass, if any
		if (sourceClass.getMetadata().hasSuperClass()) {
			String superclass = sourceClass.getMetadata().getSuperClassName();
			if (!superclass.startsWith("java") && !this.knownSuperclasses.containsKey(superclass)) {
				this.knownSuperclasses.put(superclass, configClass);
				// Superclass found, return its annotation metadata and recurse
				return sourceClass.getSuperClass();
			}
		}

		// No superclass -> processing is complete
		return null;
	}

	/**
	 * Register member (nested) classes that happen to be configuration classes themselves.
	 */
	private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		for (SourceClass memberClass : sourceClass.getMemberClasses()) {
			if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
					!memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
				if (this.importStack.contains(configClass)) {
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				}
				else {
					this.importStack.push(configClass);
					try {
						//首先递归地处理任何成员(嵌套的)类
						processConfigurationClass(memberClass.asConfigClass(configClass));
					}
					finally {
						this.importStack.pop();
					}
				}
			}
		}
	}

	/**
	 * Register default methods on interfaces implemented by the configuration class.
	 */
	private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		for (SourceClass ifc : sourceClass.getInterfaces()) {
			Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
			for (MethodMetadata methodMetadata : beanMethods) {
				if (!methodMetadata.isAbstract()) {
					// A default method or other concrete method on a Java 8+ interface...
					configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
				}
			}
			processInterfaces(configClass, ifc);
		}
	}

	/**
	 * Retrieve the metadata for all <code>@Bean</code> methods.
	 */
	private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
		AnnotationMetadata original = sourceClass.getMetadata();
		Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
		if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
			// Try reading the class file via ASM for deterministic declaration order...
			// Unfortunately, the JVM's standard reflection returns methods in arbitrary
			// order, even between different runs of the same application on the same JVM.
			try {
				AnnotationMetadata asm =
						this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
				Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
				if (asmMethods.size() >= beanMethods.size()) {
					Set<MethodMetadata> selectedMethods = new LinkedHashSet<MethodMetadata>(asmMethods.size());
					for (MethodMetadata asmMethod : asmMethods) {
						for (MethodMetadata beanMethod : beanMethods) {
							if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
								selectedMethods.add(beanMethod);
								break;
							}
						}
					}
					if (selectedMethods.size() == beanMethods.size()) {
						// All reflection-detected methods found in ASM method set -> proceed
						beanMethods = selectedMethods;
					}
				}
			}
			catch (IOException ex) {
				logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
				// No worries, let's continue with the reflection metadata we started with...
			}
		}
		return beanMethods;
	}


	/**
	 * Process the given <code>@PropertySource</code> annotation metadata.
	 * @param propertySource metadata for the <code>@PropertySource</code> annotation found
	 * @throws IOException if loading a property source failed
	 */
	private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
		String[] locations = propertySource.getStringArray("value");
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
				DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

		for (String location : locations) {
			try {
				String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			}
			catch (IllegalArgumentException ex) {
				// Placeholders not resolvable
				if (ignoreResourceNotFound) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				}
				else {
					throw ex;
				}
			}
			catch (IOException ex) {
				// Resource not found when trying to open it
				if (ignoreResourceNotFound &&
						(ex instanceof FileNotFoundException || ex instanceof UnknownHostException)) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				}
				else {
					throw ex;
				}
			}
		}
	}

	private void addPropertySource(PropertySource<?> propertySource) {
		String name = propertySource.getName();
		MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();
		if (propertySources.contains(name) && this.propertySourceNames.contains(name)) {
			// We've already added a version, we need to extend it
			PropertySource<?> existing = propertySources.get(name);
			PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ?
					((ResourcePropertySource) propertySource).withResourceName() : propertySource);
			if (existing instanceof CompositePropertySource) {
				((CompositePropertySource) existing).addFirstPropertySource(newSource);
			}
			else {
				if (existing instanceof ResourcePropertySource) {
					existing = ((ResourcePropertySource) existing).withResourceName();
				}
				CompositePropertySource composite = new CompositePropertySource(name);
				composite.addPropertySource(newSource);
				composite.addPropertySource(existing);
				propertySources.replace(name, composite);
			}
		}
		else {
			if (this.propertySourceNames.isEmpty()) {
				propertySources.addLast(propertySource);
			}
			else {
				String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
				propertySources.addBefore(firstProcessed, propertySource);
			}
		}
		this.propertySourceNames.add(name);
	}


	/**
	 * Returns {@code @Import} class, considering all meta-annotations.
	 */
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
		Set<SourceClass> imports = new LinkedHashSet<SourceClass>();
		Set<SourceClass> visited = new LinkedHashSet<SourceClass>();
		collectImports(sourceClass, imports, visited);
		return imports;
	}

	/**
	 * Recursively collect all declared {@code @Import} values. Unlike most
	 * meta-annotations it is valid to have several {@code @Import}s declared with
	 * different values; the usual process of returning values from the first
	 * meta-annotation on a class is not sufficient.
	 * <p>For example, it is common for a {@code @Configuration} class to declare direct
	 * {@code @Import}s in addition to meta-imports originating from an {@code @Enable}
	 * annotation.
	 * @param sourceClass the class to search
	 * @param imports the imports collected so far
	 * @param visited used to track visited classes to prevent infinite recursion
	 * @throws IOException if there is any problem reading metadata from the named class
	 */
	//递归地收集所有声明的@Import值
	private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
			throws IOException {

		if (visited.add(sourceClass)) {
			for (SourceClass annotation : sourceClass.getAnnotations()) {
				String annName = annotation.getMetadata().getClassName();
				if (!annName.startsWith("java") && !annName.equals(Import.class.getName())) {
					collectImports(annotation, imports, visited);
				}
			}
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}

	private void processDeferredImportSelectors() {
		List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
		this.deferredImportSelectors = null;
		//按照Order注解或者Ordered接口进行排序
		Collections.sort(deferredImports, DEFERRED_IMPORT_COMPARATOR);
        //循环处理每个DeferredImportSelector
		for (DeferredImportSelectorHolder deferredImport : deferredImports) {
			ConfigurationClass configClass = deferredImport.getConfigurationClass();
			try {
				// getImportSelector得到的是EnableAutoConfigurationImportSelector
				//此处是关键代码，执行DeferredImportSelector实现类的selectImports方法
				//调用DeferredImportSelector的方法selectImports,获取需要被导入的类的名称
				String[] imports = deferredImport.getImportSelector().selectImports(configClass.getMetadata());
				// 处理任何一个 @Import 注解
				processImports(configClass, asSourceClass(configClass), asSourceClasses(imports), false);
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]", ex);
			}
		}
	}
/**
 * 处理配置类上搜集到的@Import注解
 * 参数 configuClass 配置类
 * 参数 currentSourceClass 当前源码类
 * 参数 importCandidates, 所有的@Import注解的value
 * 参数 checkForCircularImports, 是否检查循环导入
 **/
	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, boolean checkForCircularImports) {

		if (importCandidates.isEmpty()) {
			// 如果配置类上没有任何候选@Import，说明没有需要处理的导入，则什么都不用做，直接返回
			return;
		}

		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			// 如果要求做循环导入检查，并且检查到了循环依赖，报告这个问题
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		else {
			// 开始处理配置类configClass上所有的@Import importCandidates
			this.importStack.push(configClass);
			try {
				// 循环处理每一个@Import,每个@Import可能导入三种类型的类 :
				// 1. ImportSelector--ImportSelector类的selectImports方法引入的类会被解析成ConfigurationClass
				// 2. ImportBeanDefinitionRegistrar
				// 3. 其他类型，都当作配置类处理，也就是相当于使用了注解@Configuration的配置类--引入的类会被解析成ConfigurationClass
				// 下面的for循环中对这三种情况执行了不同的处理逻辑
				for (SourceClass candidate : importCandidates) {
					//1.如果是ImportSelector接口的实现类，就在此处理。ImportSelector.class用法见类注释。
					if (candidate.isAssignable(ImportSelector.class)) {
						// Candidate class is an ImportSelector -> delegate to it to determine imports
						Class<?> candidateClass = candidate.loadClass();
						//实例化这些ImportSelector的实现类
						ImportSelector selector = BeanUtils.instantiateClass(candidateClass, ImportSelector.class);
						//如果这实现类还实现了BeanFactoryAware、EnvironmentAware这些接口，就要先执行这些接口中声明的方法
						ParserStrategyUtils.invokeAwareMethods(
								selector, this.environment, this.resourceLoader, this.registry);
						//1.1
						//如果这个实现类也实现了DeferredImportSelector接口，就被加入到集合deferredImportSelectors中。该集合会在deferredImportSelectors，processConfigurationClass(ConfigurationClass configClass) 方法的结尾处调用processDeferredImportSelectors()方法统一处理这个集合里面的DeferredImportSelector对象，进行延迟调用，即所有的配置类都解析完了，才处理DeferredImportSelector对象，调用其selectImports。。。。
						//this.deferredImportSelectors != null这个条件判断。是因为processDeferredImportSelectors()方法中会将this.deferredImportSelectors置为null，processDeferredImportSelectors()方法会调用processImports方法，既然执行到了processDeferredImportSelectors()这一步，表明已经是在执行DeferredImportSelector接口的方法，不必再往集合里面加DeferredImportSelector对象了，跳过该if分支，走下面的1.2 else分支，
						// 调用selectImports方法。
						//		List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
						//		this.deferredImportSelectors = null;
						if (this.deferredImportSelectors != null && selector instanceof DeferredImportSelector) {
							this.deferredImportSelectors.add(
									new DeferredImportSelectorHolder(configClass, (DeferredImportSelector) selector));
						}
						//1.2
						else {
							//注意，这一行是关键代码！！！执行实现类的selectImports方法
							//返回值，就是到导入的配置类的全类名
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames);
							//递归调用processImports方法
							processImports(configClass, currentSourceClass, importSourceClasses, false);
						}
					}
					//2. ImportBeanDefinitionRegistrar。ImportBeanDefinitionRegistrar.class具体用法看该类的注释。
					//candidate是一个ImportBeanDefinitionRegistrar -> 则委托给它来注册额外的BeanDefinition
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Candidate class is an ImportBeanDefinitionRegistrar ->
						// delegate to it to register additional bean definitions
						Class<?> candidateClass = candidate.loadClass();
						ImportBeanDefinitionRegistrar registrar =
								BeanUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class);
						ParserStrategyUtils.invokeAwareMethods(
								registrar, this.environment, this.resourceLoader, this.registry);
						//把ImportBeanDefinitionRegistrar对象放入当前的ConfigurationClass对象，在ConfigurationClassBeanDefinitionReader.class的loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars())会将ImportBeanDefinitionRegistrar对象取出来调用它的registerBeanDefinitions方法，进行注册额外的自定义的BeanDefinition
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					// 3. 其他类型，都当作配置类处理，也就是相当于使用了注解@Configuration的配置类
					else {
						// Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
						// process it as an @Configuration class
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
						//递归调用，将candidate封装成ConfigurationClass对象，该ConfigurationClass对象的importedBy属性值就是引入他的那个类--比如Class A上使用@Import(B.class),则B的importedBy属性值就是A，注：B不是ImportSelector对象；或者是B是ImportSelector对象，它的selectImports方法引入的类的对象的importedBy属性值就是A。
						processConfigurationClass(candidate.asConfigClass(configClass));
					}
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]", ex);
			}
			finally {
				this.importStack.pop();
			}
		}
	}

	private boolean isChainedImportOnStack(ConfigurationClass configClass) {
		if (this.importStack.contains(configClass)) {
			String configClassName = configClass.getMetadata().getClassName();
			AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
			while (importingClass != null) {
				if (configClassName.equals(importingClass.getClassName())) {
					return true;
				}
				importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
			}
		}
		return false;
	}

	ImportRegistry getImportRegistry() {
		return this.importStack;
	}


	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
	 */
	private SourceClass asSourceClass(ConfigurationClass configurationClass) throws IOException {
		AnnotationMetadata metadata = configurationClass.getMetadata();
		//判断configurationClass是否是标准的注解配置类
		if (metadata instanceof StandardAnnotationMetadata) {
			return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass());
		}
		return asSourceClass(metadata.getClassName());
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link Class}.
	 */
	SourceClass asSourceClass(Class<?> classType) throws IOException {
		try {
			// Sanity test that we can reflectively read annotations,
			// including Class attributes; if not -> fall back to ASM
			for (Annotation ann : classType.getAnnotations()) {
				AnnotationUtils.validateAnnotation(ann);
			}
			return new SourceClass(classType);
		}
		catch (Throwable ex) {
			// Enforce ASM via class name resolution
			return asSourceClass(classType.getName());
		}
	}

	/**
	 * Factory method to obtain {@link SourceClass}s from class names.
	 */
	private Collection<SourceClass> asSourceClasses(String[] classNames) throws IOException {
		List<SourceClass> annotatedClasses = new ArrayList<SourceClass>(classNames.length);
		for (String className : classNames) {
			annotatedClasses.add(asSourceClass(className));
		}
		return annotatedClasses;
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a class name.
	 */
	SourceClass asSourceClass(String className) throws IOException {
		if (className.startsWith("java")) {
			// Never use ASM for core java types
			//对于核心Java类不使用ASM，因为肯定存在的，直接加载
			try {
				return new SourceClass(this.resourceLoader.getClassLoader().loadClass(className));
			}
			catch (ClassNotFoundException ex) {
				throw new NestedIOException("Failed to load class [" + className + "]", ex);
			}
		}
		return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
	}


	@SuppressWarnings("serial")
	private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {

		private final MultiValueMap<String, AnnotationMetadata> imports =
				new LinkedMultiValueMap<String, AnnotationMetadata>();

		public void registerImport(AnnotationMetadata importingClass, String importedClass) {
			this.imports.add(importedClass, importingClass);
		}

		@Override
		public AnnotationMetadata getImportingClassFor(String importedClass) {
			List<AnnotationMetadata> list = this.imports.get(importedClass);
			return (!CollectionUtils.isEmpty(list) ? list.get(list.size() - 1) : null);
		}

		@Override
		public void removeImportingClass(String importingClass) {
			for (List<AnnotationMetadata> list : this.imports.values()) {
				for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext();) {
					if (iterator.next().getClassName().equals(importingClass)) {
						iterator.remove();
						break;
					}
				}
			}
		}

		/**
		 * Given a stack containing (in order)
		 * <ul>
		 * <li>com.acme.Foo</li>
		 * <li>com.acme.Bar</li>
		 * <li>com.acme.Baz</li>
		 * </ul>
		 * return "[Foo->Bar->Baz]".
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder("[");
			Iterator<ConfigurationClass> iterator = iterator();
			while (iterator.hasNext()) {
				builder.append(iterator.next().getSimpleName());
				if (iterator.hasNext()) {
					builder.append("->");
				}
			}
			return builder.append(']').toString();
		}
	}


	private static class DeferredImportSelectorHolder {

		private final ConfigurationClass configurationClass;

		private final DeferredImportSelector importSelector;

		public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
			this.configurationClass = configClass;
			this.importSelector = selector;
		}

		public ConfigurationClass getConfigurationClass() {
			return this.configurationClass;
		}

		public DeferredImportSelector getImportSelector() {
			return this.importSelector;
		}
	}


	/**
	 * Simple wrapper that allows annotated source classes to be dealt with
	 * in a uniform manner, regardless of how they are loaded.
	 */
	private class SourceClass {

		private final Object source;  // Class or MetadataReader

		private final AnnotationMetadata metadata;

		public SourceClass(Object source) {
			this.source = source;
			if (source instanceof Class) {
				this.metadata = new StandardAnnotationMetadata((Class<?>) source, true);
			}
			else {
				this.metadata = ((MetadataReader) source).getAnnotationMetadata();
			}
		}

		public final AnnotationMetadata getMetadata() {
			return this.metadata;
		}

		public Class<?> loadClass() throws ClassNotFoundException {
			if (this.source instanceof Class) {
				return (Class<?>) this.source;
			}
			String className = ((MetadataReader) this.source).getClassMetadata().getClassName();
			return resourceLoader.getClassLoader().loadClass(className);
		}

		public boolean isAssignable(Class<?> clazz) throws IOException {
			if (this.source instanceof Class) {
				return clazz.isAssignableFrom((Class<?>) this.source);
			}
			return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
		}
		//把SourceClass对象解析成ConfigClass并填充其importedBy属性
		public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
			if (this.source instanceof Class) {
				return new ConfigurationClass((Class<?>) this.source, importedBy);
			}
			return new ConfigurationClass((MetadataReader) this.source, importedBy);
		}

		public Collection<SourceClass> getMemberClasses() throws IOException {
			Object sourceToProcess = this.source;
			if (sourceToProcess instanceof Class) {
				Class<?> sourceClass = (Class<?>) sourceToProcess;
				try {
					Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
					List<SourceClass> members = new ArrayList<SourceClass>(declaredClasses.length);
					for (Class<?> declaredClass : declaredClasses) {
						members.add(asSourceClass(declaredClass));
					}
					return members;
				}
				catch (NoClassDefFoundError err) {
					// getDeclaredClasses() failed because of non-resolvable dependencies
					// -> fall back to ASM below
					sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
				}
			}

			// ASM-based resolution - safe for non-resolvable classes as well
			MetadataReader sourceReader = (MetadataReader) sourceToProcess;
			String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
			List<SourceClass> members = new ArrayList<SourceClass>(memberClassNames.length);
			for (String memberClassName : memberClassNames) {
				try {
					members.add(asSourceClass(memberClassName));
				}
				catch (IOException ex) {
					// Let's skip it if it's not resolvable - we're just looking for candidates
					if (logger.isDebugEnabled()) {
						logger.debug("Failed to resolve member class [" + memberClassName +
								"] - not considering it as a configuration class candidate");
					}
				}
			}
			return members;
		}

		public SourceClass getSuperClass() throws IOException {
			if (this.source instanceof Class) {
				return asSourceClass(((Class<?>) this.source).getSuperclass());
			}
			return asSourceClass(((MetadataReader) this.source).getClassMetadata().getSuperClassName());
		}

		public Set<SourceClass> getInterfaces() throws IOException {
			Set<SourceClass> result = new LinkedHashSet<SourceClass>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Class<?> ifcClass : sourceClass.getInterfaces()) {
					result.add(asSourceClass(ifcClass));
				}
			}
			else {
				for (String className : this.metadata.getInterfaceNames()) {
					result.add(asSourceClass(className));
				}
			}
			return result;
		}

		public Set<SourceClass> getAnnotations() {
			Set<SourceClass> result = new LinkedHashSet<SourceClass>();
			for (String className : this.metadata.getAnnotationTypes()) {
				try {
					result.add(getRelated(className));
				}
				catch (Throwable ex) {
					// An annotation not present on the classpath is being ignored
					// by the JVM's class loading -> ignore here as well.
				}
			}
			return result;
		}

		public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
			Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
			if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
				return Collections.emptySet();
			}
			String[] classNames = (String[]) annotationAttributes.get(attribute);
			Set<SourceClass> result = new LinkedHashSet<SourceClass>();
			for (String className : classNames) {
				result.add(getRelated(className));
			}
			return result;
		}

		private SourceClass getRelated(String className) throws IOException {
			if (this.source instanceof Class) {
				try {
					Class<?> clazz = ((Class<?>) this.source).getClassLoader().loadClass(className);
					return asSourceClass(clazz);
				}
				catch (ClassNotFoundException ex) {
					// Ignore -> fall back to ASM next, except for core java types.
					if (className.startsWith("java")) {
						throw new NestedIOException("Failed to load class [" + className + "]", ex);
					}
					return new SourceClass(metadataReaderFactory.getMetadataReader(className));
				}
			}
			return asSourceClass(className);
		}

		@Override
		public boolean equals(Object other) {
			return (this == other || (other instanceof SourceClass &&
					this.metadata.getClassName().equals(((SourceClass) other).metadata.getClassName())));
		}

		@Override
		public int hashCode() {
			return this.metadata.getClassName().hashCode();
		}

		@Override
		public String toString() {
			return this.metadata.getClassName();
		}
	}


	/**
	 * {@link Problem} registered upon detection of a circular {@link Import}.
	 */
	private static class CircularImportProblem extends Problem {

		public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
			super(String.format("A circular @Import has been detected: " +
					"Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
					"already present in the current import stack %s", importStack.element().getSimpleName(),
					attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack),
					new Location(importStack.element().getResource(), attemptedImport.getMetadata()));
		}
	}

}
