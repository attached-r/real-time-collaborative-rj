package rj.collaborative;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.Scanner;

@SpringBootApplication
public class RealTimeCollaborativeDocApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(RealTimeCollaborativeDocApplication.class);
        app.run(args);

    }
}
