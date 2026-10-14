# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] 2026-10-14
- Ongoing development for the 0.0.1 version alpha.
- **Added runnable quickstart example application (`examples/quickstart`)** in a clean monorepo structure.
- Added local project dependency wiring in `build.sbt` using `dependsOn` for instant feedback without artifact publishing.
- Added Docker Compose support and step-by-step instructions for local OpenID Connect testing using Keycloak.
- Added example controllers (`HomeController.scala`) and Twirl views (`index.scala.html`) to display active OIDC session claims.

---

## [0.0.1 alpha] 2026-10-13

### Added
- Initial release of the `play-auth-sso` module.
- Scala 3.3.7, Play 3.0.x (Apache Pekko backend).
- OIDC authorization-code login/logout via the Nimbus `oauth2-oidc-sdk`, with provider metadata resolved lazily from the discovery endpoint.
- `OidcController` with `login`, `callback`, and `logout` actions, including `state`/`nonce` CSRF handling and ID-token validation.
- `OidcService` wrapping the Nimbus authorization-code flow.
- `SessionCreator` SPI (default stores `sub` plus configured claims in the Play session), overridable via Guice.
- `NimbusModule` for automatic Guice dependency injection, registered through `reference.conf`.