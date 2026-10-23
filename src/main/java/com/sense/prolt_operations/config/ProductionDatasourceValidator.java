package com.sense.prolt_operations.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionDatasourceValidator implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (environment == null) {
            return;
        }
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        if (url == null || url.isBlank()) {
            throw new IllegalStateException("FATAL: Production datasource URL (spring.datasource.url / DATABASE_URL) must not be blank!");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("FATAL: Production datasource username (spring.datasource.username / POSTGRES_USER) must not be blank!");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("FATAL: Production datasource password (spring.datasource.password / POSTGRES_PASSWORD) must not be blank!");
        }
    }
}
