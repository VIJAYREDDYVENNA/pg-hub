package com.hostelmanagement.notification.whatsapp;

import com.hostelmanagement.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class WhatsAppConfig {

    @Bean
    public WhatsAppProvider whatsAppProvider(AppProperties props) {
        if (props.whatsapp().isMock()) {
            log.info("WhatsApp integration running in MOCK mode: messages are simulated and NOT sent");
            return new MockWhatsAppProvider();
        }
        MetaCloudWhatsAppProvider provider = new MetaCloudWhatsAppProvider(props.whatsapp());
        if (!provider.isConfigured()) {
            log.warn("WHATSAPP_MODE=META but credentials are missing; messages will fail until configured");
        }
        return provider;
    }
}
