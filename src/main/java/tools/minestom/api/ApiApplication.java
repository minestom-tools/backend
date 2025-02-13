package tools.minestom.api;

import net.minestom.server.MinecraftServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiApplication {

    public static void main(String[] args) {
        MinecraftServer server = MinecraftServer.init();
        server.start("0.0.0.0", 0);
        SpringApplication.run(ApiApplication.class, args);
    }

}
