package com.ognjen.diplomski.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "ai.lifecycle.scheduling.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AiSchedulingConfig {}
