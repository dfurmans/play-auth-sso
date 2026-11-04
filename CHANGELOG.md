# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] 2026-11-04

### Changed
- `OidcConfig` fields are now Scala 3 opaque types (`Issuer`, `ClientId`, `CallbackUrl`, `PostLoginUrl`, `PostLogoutUrl`, `OidcScope`, `ClaimName`) instead of bare `String`/`Seq[String]`. `clientSecret` is a dedicated `ClientSecret` case class whose `toString` redacts the value so it can't leak into logs.
- `OidcConfigProvider` now validates configuration at startup: required fields must be non-empty, and `issuer`/`callback_url` must be absolute URIs (`post_login_url`/`post_logout_url` may be relative). Invalid or missing configuration fails fast with `IllegalArgumentException` instead of failing later at first use.
- `OidcController#login` and `#logout` are now `Action.async` and run IdP calls (discovery, token exchange, JWKS fetch) on a dedicated `nimbus.blocking-dispatcher` thread pool instead of Play's default dispatcher.
- `OidcService` caches a single `IDTokenValidator` backed by a cached `JWKSource`, instead of rebuilding the validator (and re-fetching the JWKS) on every callback. The signing algorithm is taken from the provider metadata (`RS256` preferred, falling back to the first advertised algorithm) instead of being hardcoded.
- The IdP end-session (logout) URL is now built with proper URL-encoding of `post_logout_redirect_uri` and `id_token_hint`, and appends to an existing query string on the end-session endpoint correctly.
- The logout flow now sends a real `id_token_hint`: the callback stores the raw ID token in a new `IdTokenStore` SPI (default: in-memory with TTL `nimbus.oidc.id_token_ttl`, overridable via Guice) and keeps only an opaque key (`oidc_sid`) in the session cookie. Logout looks the token up, sends it as `id_token_hint`, and removes it.

- Login now uses PKCE (S256). The code verifier is kept in the session (`oidc_pkce`) and sent with the token request.
- The callback now handles `error` responses from the IdP explicitly, and always clears the one-time `oidc_state`, `oidc_nonce` and `oidc_pkce` session values, on success and on failure.
- Discovery and token-endpoint HTTP calls now have a 5 second connect/read timeout.
- `OidcService#buildAuthorizationUrl` and `#exchangeCode` take an additional PKCE `CodeVerifier` argument.

### Removed
- Removed the hardcoded default `nimbus.oidc.client_secret` value from `reference.conf`. It now defaults to empty and is read from the `OIDC_CLIENT_SECRET` environment variable; the previously committed value should be treated as leaked and rotated.

### Added
- Controller tests (`OidcControllerSpec`) for the callback validation paths and session cleanup.
- Unit tests for the new config validation (`OidcConfigSpec`), the `DefaultSessionCreator` claim/redirect behavior (`DefaultSessionCreatorSpec`), and the new `OidcService` logout-URL encoding and signing-algorithm selection helpers (`OidcServiceSpec`).

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