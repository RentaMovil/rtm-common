# rtm-common

Shared infrastructure code for RentaMovil's Spring Boot microservices. Extracted from the
near-identical duplication found across `rtm-iam`, `rtm-fleet-maintenance`,
`rtm-booking-reservation`, `rtm-payment-billing` and `rtm-telemetry-gps` (see ADR-011 in
`rtm-docs/05-architecture/decisions/records/`).

## What's in this first version

| Class | Package | Replaces |
|---|---|---|
| `ErrorResponse` | `com.rentamovil.common.web.dto` | Each service's own identical `ErrorResponse` DTO |
| `BusinessRuleException`, `ForbiddenException`, `InvalidTokenException`, `ResourceAlreadyExistsException`, `ResourceNotFoundException` | `com.rentamovil.common.exception` | The generic domain exceptions every service redefined with the same shape |
| `BaseExceptionHandler` | `com.rentamovil.common.web.exception` | The shared ~60-70% of each service's `GlobalExceptionHandler` |
| `RequestLogFilter` | `com.rentamovil.common.web.filter` | Each service's `RequestLogFilter` (was byte-for-byte identical already) |

**Out of scope for this version** (per ADR-011): the internal HTTP client pattern
(`RestClient` + timeout + error mapping used for service-to-service calls). May be added later
once this first slice proves out.

## How a service adopts it

1. Add this repository and the dependency to the service's `pom.xml`:

   ```xml
   <repositories>
       <repository>
           <id>github</id>
           <url>https://maven.pkg.github.com/RentaMovil/rtm-common</url>
       </repository>
   </repositories>

   <dependencies>
       <dependency>
           <groupId>com.rentamovil</groupId>
           <artifactId>rtm-common</artifactId>
           <version>0.1.0</version>
       </dependency>
   </dependencies>
   ```

2. Authenticate to GitHub Packages (read:packages scope) in `~/.m2/settings.xml` locally, and via
   the `GITHUB_TOKEN` secret in CI — same mechanism as `.github/workflows/publish.yml` in this repo.

3. In the service's own `GlobalExceptionHandler`:
   - Extend `BaseExceptionHandler` instead of handling the generic cases itself.
   - Keep `@RestControllerAdvice` on the concrete class — Spring does not inherit that annotation
     from the base class.
   - Only keep `@ExceptionHandler` methods for exceptions specific to that service's own domain.
   - Override `mapIntegrityViolation(String cause)` if the service needs to recognize its own DB
     constraint names (e.g. `uq_vehicle_plate`); otherwise a generic `CONFLICT` is returned.
   - Delete the service's own `ErrorResponse` and the generic exception classes now provided here,
     and update imports accordingly.

4. Delete the service's own `RequestLogFilter` and register this one instead. It is **not**
   `@Component`-annotated on purpose — `com.rentamovil.common` sits outside every service's own
   component-scan base package (`com.rentamovil.<service>`), and widening the scan to pick it up
   would be an easy way to accidentally pull in other, unrelated beans later as this library
   grows. Register it explicitly in a small `@Configuration` class instead:

   ```java
   @Configuration
   public class WebConfig {
       @Bean
       public RequestLogFilter requestLogFilter() {
           return new RequestLogFilter();
       }
   }
   ```

## Versioning and releases

Independent SemVer, same as every other RentaMovil repo (`00-governance/git-conventions.md`):

```bash
git tag -a v0.1.0 -m "Release v0.1.0: initial GlobalExceptionHandler and RequestLogFilter extraction"
git push origin v0.1.0
```

Pushing the tag triggers `.github/workflows/publish.yml`, which deploys the jar to GitHub Packages
for this repository. Consuming services bump their own `<version>` for `rtm-common` whenever
they're ready — there is no forced, simultaneous upgrade across services.
