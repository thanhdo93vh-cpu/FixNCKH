package com.example.hauiTrash.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {
    @Bean
    public Cloudinary cloudinary(){
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "gvvcogm9",
                "api_key", "481936985296416",
                "api_secret", "I0InO7tWcaNbEQfvO8gjdeq0Jf8",
                "secure", true
        ));
    }
}