
# Run the Spring Boot application locally (dev profile)
dev:
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run the Spring Boot application against a local DB
local:
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5432/quarkus --spring.datasource.username=quarkus --spring.datasource.password=quarkus"
