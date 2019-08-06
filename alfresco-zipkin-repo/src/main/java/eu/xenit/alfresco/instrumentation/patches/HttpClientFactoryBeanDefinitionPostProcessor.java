package eu.xenit.alfresco.instrumentation.patches;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import java.util.Arrays;


public class HttpClientFactoryBeanDefinitionPostProcessor implements BeanDefinitionRegistryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(HttpClientFactoryBeanDefinitionPostProcessor.class);

    private String httpClientFactoryBeanName;
    private String beanClassName;
    private String beanNewClassName;

    public HttpClientFactoryBeanDefinitionPostProcessor(String httpClientFactoryBeanName, String beanClassName, String beanNewClassName) {
        this.httpClientFactoryBeanName = httpClientFactoryBeanName;
        this.beanClassName = beanClassName;
        this.beanNewClassName = beanNewClassName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException
    {
        // NO-OP
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry) throws BeansException
    {
        logAll(registry);
        if (registry.containsBeanDefinition(httpClientFactoryBeanName))
        {
            final BeanDefinition beanDefinition = registry.getBeanDefinition(httpClientFactoryBeanName);
            if (beanClassName.equals(beanDefinition.getBeanClassName())) {
                beanDefinition.setBeanClassName(beanNewClassName);
            }
        }
    }

    private static void logAll(BeanDefinitionRegistry registry) {
        if (log.isDebugEnabled())
            Arrays.stream(registry.getBeanDefinitionNames()).forEach(n -> log(registry,n));

    }

    private static void log(BeanDefinitionRegistry registry, String beanName) {
        log.debug(beanName+ " -> "+registry.getBeanDefinition(beanName).getBeanClassName());
    }
}
