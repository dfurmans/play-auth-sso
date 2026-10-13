# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
- Ongoing development for the 0.0.1 version alpha 

## [0.0.1 alpha] - 2026-10-13
### Added
- Initial release of the `play-auth-sso` module.
- Scala 3.3.7, Play 3.0.x (Apache Pekko backend).
- OIDC authorization-code login/logout via the Nimbus `oauth2-oidc-sdk`, with
  provider metadata resolved lazily from the discovery endpoint.
- `OidcController` with `login`, `callback`, and `logout` actions, including
  `state`/`nonce` CSRF handling and ID-token validation.
- `OidcService` wrapping the Nimbus authorization-code flow.
- `SessionCreator` SPI (default stores `sub` plus configured claims in the Play
  session), overridable via Guice.
- `NimbusModule` for automatic Guice dependency injection, registered through
  `reference.conf`.
