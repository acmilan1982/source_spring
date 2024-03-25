//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.springframework.web.context.support;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.ui.context.Theme;
import org.springframework.ui.context.ThemeSource;
import org.springframework.ui.context.support.UiApplicationContextUtils;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ServletContextAware;

public class GenericWebApplicationContext extends GenericApplicationContext implements ConfigurableWebApplicationContext, ThemeSource {
    @Nullable
    private ServletContext servletContext;
    @Nullable
    private ThemeSource themeSource;

    // 构造方法
    public GenericWebApplicationContext() {
        // 调用父类 GenericApplicationContext 构造方法
    }

    public GenericWebApplicationContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public GenericWebApplicationContext(DefaultListableBeanFactory beanFactory) {
        super(beanFactory);
    }

    public GenericWebApplicationContext(DefaultListableBeanFactory beanFactory, ServletContext servletContext) {
        super(beanFactory);
        this.servletContext = servletContext;
    }

    public void setServletContext(@Nullable ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Nullable
    public ServletContext getServletContext() {
        return this.servletContext;
    }

    public String getApplicationName() {
        return this.servletContext != null ? this.servletContext.getContextPath() : "";
    }

    protected ConfigurableEnvironment createEnvironment() {
        return new StandardServletEnvironment();
    }

    protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (this.servletContext != null) {
            beanFactory.addBeanPostProcessor(new ServletContextAwareProcessor(this.servletContext));
            beanFactory.ignoreDependencyInterface(ServletContextAware.class);
        }

        WebApplicationContextUtils.registerWebApplicationScopes(beanFactory, this.servletContext);
        WebApplicationContextUtils.registerEnvironmentBeans(beanFactory, this.servletContext);
    }

    protected Resource getResourceByPath(String path) {
        Assert.state(this.servletContext != null, "No ServletContext available");
        return new ServletContextResource(this.servletContext, path);
    }

    protected ResourcePatternResolver getResourcePatternResolver() {
        return new ServletContextResourcePatternResolver(this);
    }

    protected void onRefresh() {
        this.themeSource = UiApplicationContextUtils.initThemeSource(this);
    }

    protected void initPropertySources() {
        ConfigurableEnvironment env = this.getEnvironment();
        if (env instanceof ConfigurableWebEnvironment) {
            ((ConfigurableWebEnvironment)env).initPropertySources(this.servletContext, (ServletConfig)null);
        }

    }

    @Nullable
    public Theme getTheme(String themeName) {
        Assert.state(this.themeSource != null, "No ThemeSource available");
        return this.themeSource.getTheme(themeName);
    }

    public void setServletConfig(@Nullable ServletConfig servletConfig) {
    }

    @Nullable
    public ServletConfig getServletConfig() {
        throw new UnsupportedOperationException("GenericWebApplicationContext does not support getServletConfig()");
    }

    public void setNamespace(@Nullable String namespace) {
    }

    @Nullable
    public String getNamespace() {
        throw new UnsupportedOperationException("GenericWebApplicationContext does not support getNamespace()");
    }

    public void setConfigLocation(String configLocation) {
        if (StringUtils.hasText(configLocation)) {
            throw new UnsupportedOperationException("GenericWebApplicationContext does not support setConfigLocation(). Do you still have a 'contextConfigLocation' init-param set?");
        }
    }

    public void setConfigLocations(String... configLocations) {
        if (!ObjectUtils.isEmpty(configLocations)) {
            throw new UnsupportedOperationException("GenericWebApplicationContext does not support setConfigLocations(). Do you still have a 'contextConfigLocations' init-param set?");
        }
    }

    public String[] getConfigLocations() {
        throw new UnsupportedOperationException("GenericWebApplicationContext does not support getConfigLocations()");
    }
}
