=
 package org.springframework.core.annotation;

 import java.lang.annotation.Annotation;
 import java.lang.annotation.Documented;
 import java.lang.annotation.ElementType;
 import java.lang.annotation.Retention;
 import java.lang.annotation.RetentionPolicy;
 import java.lang.annotation.Target;
 
 /**
    @AliasFor is an annotation that is used to declare aliases for annotation attributes.    @AliasFor用来声明注解属性的别名
   

    Usage Scenarios
     ● Explicit aliases within an annotation:     同一个注解对象内的显式别名

       within a single annotation, @AliasFor can be declared on a pair of attributes to signal that they are interchangeable aliases for each other.
       在一个注解中，可以在一对属性上声明@AliasFor，以表示它们是可互换的别名。

     ● Explicit alias for attribute in meta-annotation:    元注解属性的别名
       

       if the annotation attribute of @AliasFor is set to a different annotation than the one that declares it, 
       the attribute is interpreted as an alias for an attribute in a meta-annotation (i.e., an explicit meta-annotation attribute override). 
       This enables fine-grained control over exactly which attributes are overridden within an annotation hierarchy. 
       In fact, with @AliasFor it is even possible to declare an alias for the value attribute of a meta-annotation.
       如果@AliasFor的annotation属性，设置为另一个注解而不是声明它的注解，
       那么attribute会被解释成元注释中的属性的别名（即显式元注释属性覆盖）。
       这允许对注释层次结构中覆盖的确切属性进行细粒度控制。
       事实上，使用@AliasFor，甚至可以为元注解的value属性声明别名。

    ● Implicit aliases within an annotation:    同一个注解对象内的隐式别名
      if one or more attributes within an annotation are declared as attribute overrides for the same meta-annotation attribute (either directly or transitively), 
      those attributes will be treated as a set of implicit aliases for each other, resulting in behavior analogous to that for explicit aliases within an annotation.
      如果一个注解注释内，一个或多个属性被声明为同一元注释属性的属性覆盖（直接地或传递地），
      这些属性将被视为彼此的一组隐式别名，从而导致类似于注释中显式别名的行为。


    Usage Requirements
      Like with any annotation in Java, the mere presence of @AliasFor on its own will not enforce alias semantics. 
      For alias semantics to be enforced, annotations must be loaded via MergedAnnotations.
      与Java中的任何注释一样，仅仅存在@AliasFor本身并不能强制执行别名语义。
      为了强制执行别名语义，必须通过MergedAnnotations加载注解。

    Implementation Requirements
     ● Explicit aliases within an annotation:    同一个注解对象内的显式别名
        1. Each attribute that makes up an aliased pair should be annotated with @AliasFor, and either attribute or value must reference the other attribute in the pair. 
           Since Spring Framework 5.2.1 it is technically possible to annotate only one of the attributes in an aliased pair; 
           however, it is recommended to annotate both attributes in an aliased pair for better documentation as well as compatibility with previous versions of the Spring Framework.
        2. Aliased attributes must declare the same return type.
        3. Aliased attributes must declare a default value.
        4. Aliased attributes must declare the same default value.
        5. annotation should not be declared.

        1. 组成别名对的每个属性都应该用@AliasFor进行注释，并且attribute或must必须引用对中的另一个属性。
           从Spring Framework 5.2.1起，在技术上可以只注解别名对中的一个属性；但是，建议在别名对中对这两个属性进行注释，以获得更好的文档以及与以前版本的Spring Framework的兼容性。
        2. 别名属性必须声明相同的返回类型。
        3. 别名属性必须声明一个默认值。
        4. 别名属性必须声明相同的默认值。
        5. 不应声明注释。

        
    Explicit alias for attribute in meta-annotation:  元注解属性的别名
      1.The attribute that is an alias for an attribute in a meta-annotation must be annotated with @AliasFor, and attribute must reference the attribute in the meta-annotation.
      2.Aliased attributes must declare the same return type.
      3.annotation must reference the meta-annotation.
      4. The referenced meta-annotation must be meta-present on the annotation class that declares @AliasFor.

      1.作为元注解中某个属性的别名，属性必须使用@AliasFor进行注释，并且attribute必须引用元注释中的该属性。
      2.别名属性必须声明相同的返回类型。
      3.annotation必须指向元注解。
      4.被引用的元注解，必须是声明@AliasFor的注解类上存在的meta-present。

    Implicit aliases within an annotation:  同一个注解对象内的隐式别名
      1. Each attribute that belongs to a set of implicit aliases must be annotated with @AliasFor, and attribute must reference the same attribute in the same meta-annotation (either directly or transitively via other explicit meta-annotation attribute overrides within the annotation hierarchy).
      2. Aliased attributes must declare the same return type.
      3. Aliased attributes must declare a default value.
      4. Aliased attributes must declare the same default value.
      5. annotation must reference an appropriate meta-annotation.
      6. The referenced meta-annotation must be meta-present on the annotation class that declares @AliasFor.

      属于一组隐式别名的每个属性都必须使用@AliasFor进行注释，并且属性必须引用同一元注释中的同一属性（直接或通过注释层次结构中的其他显式元注释属性重写进行传递）。
      1. 别名属性必须声明相同的返回类型。
      2. 别名属性必须声明一个默认值。
      3. 别名属性必须声明相同的默认值。
      4. 注释必须引用适当的元注释。
      5. 引用的元注释必须是声明@AliasFor的注释类上存在的元注释。


    Example: Explicit Aliases within an Annotation  同一个注解对象内的显式别名
    
    In @ContextConfiguration, value and locations are explicit aliases for each other.
    public @interface ContextConfiguration {
    
        @AliasFor("locations")
        String[] value() default {};
    
        @AliasFor("value")
        String[] locations() default {};
    
        // ...
    }
    value，locations互为显式别名

    Example: Explicit Alias for Attribute in Meta-annotation       元注解属性的别名
    In @XmlTestConfig, xmlFiles is an explicit alias for locations in @ContextConfiguration. In other words, xmlFiles overrides the locations attribute in @ContextConfiguration.
    @ContextConfiguration
    public @interface XmlTestConfig {
    
        @AliasFor(annotation = ContextConfiguration.class, attribute = "locations")
        String[] xmlFiles();
    }
    @XmlTestConfig中，xmlFiles是@ContextConfiguration中locations的显式别名，
    即xmlFiles会覆盖@ContextConfiguration中locations的值





    Example: Implicit Aliases within an Annotation
    In @MyTestConfig, value, groovyScripts, and xmlFiles are all explicit meta-annotation attribute overrides for the locations attribute in @ContextConfiguration. 
    These three attributes are therefore also implicit aliases for each other.
    @ContextConfiguration
    public @interface MyTestConfig {
    
        @AliasFor(annotation = ContextConfiguration.class, attribute = "locations")
        String[] value() default {};
    
        @AliasFor(annotation = ContextConfiguration.class, attribute = "locations")
        String[] groovyScripts() default {};
    
        @AliasFor(annotation = ContextConfiguration.class, attribute = "locations")
        String[] xmlFiles() default {};
    }
    在@MyTestConfig中，value、groovyScripts和xmlFiles都是对@ContextConfiguration中locations属性的显式元注释属性重写。
    因此，这三个属性也是彼此的隐式别名。



    Example: Transitive Implicit Aliases within an Annotation
    In @GroovyOrXmlTestConfig, groovy is an explicit override for the groovyScripts attribute in @MyTestConfig; 
    whereas, xml is an explicit override for the locations attribute in @ContextConfiguration.
    Furthermore, groovy and xml are transitive implicit aliases for each other, since they both effectively override the locations attribute in @ContextConfiguration.
    
    @MyTestConfig
    public @interface GroovyOrXmlTestConfig {
    
        @AliasFor(annotation = MyTestConfig.class, attribute = "groovyScripts")
        String[] groovy() default {};
    
        @AliasFor(annotation = ContextConfiguration.class, attribute = "locations")
        String[] xml() default {};
    }




    Spring Annotations Supporting Attribute Aliases
      As of Spring Framework 4.2, several annotations within core Spring have been updated to use @AliasFor to configure their internal attribute aliases. 
      Consult the Javadoc for individual annotations as well as the reference manual for details.
      
    Since:
    4.2
    See Also:
    MergedAnnotations, SynthesizedAnnotation
    Author:
    Sam Brannen
  */
 @Retention(RetentionPolicy.RUNTIME)
 @Target(ElementType.METHOD)
 @Documented
 public @interface AliasFor {
 
     /**
          Alias for attribute.
          Intended to be used instead of attribute when annotation is not declared 
          — for example: @AliasFor("value") instead of @AliasFor(attribute = "value").

          当annotation()未指定时候，用作attribute的别名，
      */
     @AliasFor("attribute")
     String value() default "";
 
     /**
      * The name of the attribute that <em>this</em> attribute is an alias for.
      * @see #value
      */
     @AliasFor("value")
     String attribute() default "";
 
     /**
          The type of annotation in which the aliased attribute is declared.
          Defaults to Annotation, implying that the aliased attribute is declared in the same annotation as this attribute.

          被别名属性所在的注解
          默认值为Annotation，表示被别名的属性与当前属性在同一个注解对象中
      */
     Class<? extends Annotation> annotation() default Annotation.class;
 
 }
 