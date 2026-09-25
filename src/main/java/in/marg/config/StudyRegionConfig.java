package in.marg.config;

import in.marg.model.GeoPoint;
import in.marg.model.StudyRegion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StudyRegionConfig {
    @Bean
    public StudyRegion kazirangaKarbiAnglong() {
        // Prototype study window. A/B are deliberately near known localities,
        // not a construction proposal or legal project alignment.
        return new StudyRegion(
                "Kaziranga–Karbi Anglong, Assam",
                25.75,
                93.10,
                26.65,
                93.75,
                new GeoPoint(26.58970, 93.40035), // A: Kohora Chariali area
                new GeoPoint(25.84573, 93.43781)  // B: Diphu area
        );
    }
}
