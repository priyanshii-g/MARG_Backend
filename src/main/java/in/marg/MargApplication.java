package in.marg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MargApplication {
    public static void main(String[] args) {
        SpringApplication.run(MargApplication.class, args);
    }
}
