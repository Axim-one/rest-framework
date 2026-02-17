package one.axim.framework.rest.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

@Configuration
public class MessageSourceConfiguration {

    /**
     * Parent: framework built-in default messages (framework-messages.properties)
     * Child:  application messages (configurable via spring.messages.basename, defaults to "messages")
     *
     * Lookup order: application messages → framework defaults
     * Applications can override any framework key by defining the same key in their messages.properties
     */
    @Bean
    public MessageSource messageSource(@Value("${spring.messages.basename:messages}") String basename,
                                       @Value("${spring.messages.encoding:UTF-8}") String encoding) {

        // Framework default messages (parent)
        ResourceBundleMessageSource frameworkMessages = new ResourceBundleMessageSource();
        frameworkMessages.setBasename("framework-messages");
        frameworkMessages.setDefaultEncoding(encoding);
        frameworkMessages.setUseCodeAsDefaultMessage(true);
        frameworkMessages.setFallbackToSystemLocale(true);

        // Application messages (child) — overrides framework defaults
        ResourceBundleMessageSource appMessages = new ResourceBundleMessageSource();
        appMessages.setBasename(basename);
        appMessages.setDefaultEncoding(encoding);
        appMessages.setAlwaysUseMessageFormat(false);
        appMessages.setUseCodeAsDefaultMessage(false); // fallback to parent, not code
        appMessages.setFallbackToSystemLocale(true);
        appMessages.setParentMessageSource(frameworkMessages);

        return appMessages;
    }
}