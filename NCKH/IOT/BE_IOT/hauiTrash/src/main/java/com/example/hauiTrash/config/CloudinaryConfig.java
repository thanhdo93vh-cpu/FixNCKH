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
                "cloud_name", "dtbyrc1ae",
                "api_key", "418518358217964",
                "api_secret", "Y2yRe1sy8C1AIvIF7eyUlVwGy20",
                "secure", true
        ));
    }

}