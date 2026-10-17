# Play Auth SSO module

[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.dfurmans/play-auth-sso_3)](https://central.sonatype.com/artifact/io.github.dfurmans/play-auth-sso_3)


[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A thin, pluggable Play Framework (Scala 3, Play 3.0.x / Pekko) module for
PAM/SSO **login and logout via OpenID Connect**. It ships a ready-to-wire
`OidcController` (`login` / `callback` / `logout`) that runs the OIDC
authorization-code flow against any OIDC-compliant IdP and creates a Play
session on success.

Authentication is delegated to the Nimbus `oauth2-oidc-sdk`. The module adds no
bespoke crypto — it wires a battle-tested library into Play.

## Compatibility

Any Play application on the compatible stack can plug this in and get SSO
login/logout against a PAM/IdP that speaks OpenID Connect
(Okta, Keycloak, Azure AD, Ping, Auth0, …).

- **Play** 3.0.x (Pekko), **Scala** 3, **JDK** 17+.
- **IdP** must support OIDC discovery (`/.well-known/openid-configuration`) and
  the authorization-code flow — every mainstream PAM/SSO system does.

The plugin handles authentication and session creation. Your app still owns
authorization (what a logged-in user may do); use the `SessionCreator` SPI if
you need custom session/authorization logic.

## Quick start (plug it into any Play app)

1. **Add the dependency** (auto-registers, no module wiring needed):

   ```scala
   libraryDependencies += "io.ellatech" %% "play-auth-sso" % "latest.release"
   ```

2. **Configure your IdP** in `conf/application.conf`:

   ```hocon
   nimbus.oidc {
     issuer        = "https://login.example.com"
     client_id     = "your-client-id"
     client_secret = "your-client-secret"
     callback_url  = "https://your-app.example.com/oidc/callback"
   }
   ```

3. **Add the routes** to `conf/routes`:

   ```
   GET   /oidc/login      io.ellatech.dfurmans.controller.OidcController.login()
   GET   /oidc/callback   io.ellatech.dfurmans.controller.OidcController.callback()
   GET   /oidc/logout     io.ellatech.dfurmans.controller.OidcController.logout()
   ```

4. **Send users to `/oidc/login`.** After the IdP authenticates them they return
   to `/oidc/callback`, a Play session is created (`sub` + configured claims),
   and the browser is redirected to `post_login_url`. `/oidc/logout` clears the
   session and signs out at the IdP.

That is a working SSO login/logout. The full guide below adds the optional
login page, the session controller, and custom session logic.

## What it does

- Redirects the browser to your IdP for login (`/oidc/login`).
- Handles the IdP callback: verifies `state` (CSRF), exchanges the code for
  tokens, validates the ID token (signature, `iss`, `aud`, `exp`, `nonce`).
- Creates a Play session via a pluggable `SessionCreator` (default stores the
  subject plus configured claims).
- Logs out via the IdP end-session endpoint (`/oidc/logout`).

It is a login/relying-party client, not a per-request API token guard.

## Features

- **OIDC authorization-code flow** on the Nimbus SDK, with provider discovery
  resolved lazily from `{issuer}/.well-known/openid-configuration`.
- **Plug & play:** auto-wires via Guice through `reference.conf`.
- **Pluggable session creation:** override the `SessionCreator` SPI to run your
  own user sync, encryption, and session keys.

## Installation

```scala
libraryDependencies += "io.ellatech" %% "play-auth-sso" % "1.0.0-SNAPSHOT"
```

Runtime dependency pulled in: `com.nimbusds:oauth2-oidc-sdk` (from Maven Central).

---

# Building locally for development

While the module is not yet published to a public repository, build it and
publish it to your local Ivy cache (`~/.ivy2/local`), then depend on the
`-SNAPSHOT` version from your Play API project on the same machine.

## Prerequisites

- JDK 17+ (`java -version`)
- sbt 1.9.x (the repo pins sbt 1.9.8 via `project/build.properties`)
- Scala 3.3.7 (resolved automatically by sbt)

## Build and test

From the module checkout:

```bash
# compile
sbt compile

# run the test suite (wiring smoke test)
sbt test

# clean rebuild from scratch
sbt clean compile test
```

## Publish to your local machine

```bash
# publishes io.ellatech:play-auth-sso_3:1.0.0-SNAPSHOT to ~/.ivy2/local
sbt publishLocal
```

The artifact id is cross-versioned for Scala 3, so from your Play app you depend
on it with `%%`:

```scala
// build.sbt of your Play API project
libraryDependencies += "io.ellatech" %% "play-auth-sso" % "1.0.0-SNAPSHOT"
```

Then start your Play app as usual (`sbt run`). sbt resolves the module from
`~/.ivy2/local`.

## Iterating on the module

After changing module code, re-run `sbt publishLocal` and restart (or reload)
the consuming app so it picks up the new SNAPSHOT. For a tighter loop you can
instead wire the module as an sbt source dependency (local project reference)
in your app's `build.sbt`:

```scala
lazy val nimbusPam = ProjectRef(file("/path/to/play-auth-sso"), "play-auth-sso")
lazy val root = (project in file(".")).dependsOn(nimbusPam)
```

With a source dependency, changes are picked up on the next `sbt run` /
`reload` — no `publishLocal` step needed.

---

# Integrating into a brand-new Play Scala project

A complete, minimal wiring. Four steps.

## 1. Add the dependency

In `build.sbt`:

```scala
libraryDependencies += "io.ellatech" %% "play-auth-sso" % "1.0.0-SNAPSHOT"
```

The module registers itself automatically — no `play.modules.enabled` entry
needed.

## 2. Configure the IdP

In `conf/application.conf`:

```hocon
nimbus.oidc {
  issuer         = "https://login.example.com"
  client_id      = "your-client-id"
  client_secret  = "your-client-secret"

  # Absolute callback URL registered with your IdP.
  callback_url   = "https://your-app.example.com/oidc/callback"

  # Where the browser lands after login / after logout.
  post_login_url  = "/"
  post_logout_url = "/"

  # Scopes requested at login.
  scopes = ["openid", "profile", "email"]

  # ID-token claims copied into the Play session (subject is always "sub").
  session_claims = ["email", "name"]
}
```

### Configuration keys

| Key | Purpose | Default |
| --- | --- | --- |
| `nimbus.oidc.issuer` | OIDC issuer URL | `""` |
| `nimbus.oidc.client_id` | OIDC client id | `""` |
| `nimbus.oidc.client_secret` | OIDC client secret (plain string) | `""` |
| `nimbus.oidc.callback_url` | Absolute callback URL registered with the IdP | `""` |
| `nimbus.oidc.post_login_url` | Redirect target after successful login | `"/"` |
| `nimbus.oidc.post_logout_url` | Redirect target after logout | `"/"` |
| `nimbus.oidc.scopes` | OIDC scopes requested at login | `["openid","profile","email"]` |
| `nimbus.oidc.session_claims` | ID-token claims copied into the session | `["email","name"]` |

## 3. Add the routes

In `conf/routes` — the module controller is
`io.ellatech.dfurmans.controller.OidcController`:

```
# PAM/SSO integration via OIDC (Nimbus SDK)
GET   /oidc/login      io.ellatech.dfurmans.controller.OidcController.login()
GET   /oidc/callback   io.ellatech.dfurmans.controller.OidcController.callback()
GET   /oidc/logout     io.ellatech.dfurmans.controller.OidcController.logout()
GET   /callback        io.ellatech.dfurmans.controller.OidcController.callback()
POST  /callback        io.ellatech.dfurmans.controller.OidcController.callback()
GET   /centralLogout   io.ellatech.dfurmans.controller.OidcController.logout()

# Your app's session + login page
GET   /login           your.canonical.path.SessionController.login()
GET   /logout          your.canonical.path.SessionController.logout()
GET   /                your.canonical.path.SessionController.home()
```

That is all you need for login and logout to work. The sections below show an
optional host `SessionController` and a login/logout view that drive it.

## 4. A simple login/logout page (optional)

The module provides the OIDC machinery; your app provides the landing page. A
minimal `SessionController` that renders a page with "Login with SSO" and
"Logout" links, and reads the session the module created:

```scala
package controllers

import javax.inject._
import play.api.mvc._
import play.twirl.api.Html

@Singleton
class SessionController @Inject() (cc: ControllerComponents) extends AbstractController(cc) {

  def home: Action[AnyContent] = Action { implicit request =>
    // The module stores "sub" (and any configured session_claims) on login.
    request.session.get("sub") match {
      case Some(subject) => Ok(views.html.session.loggedIn(subject))
      case None          => Redirect(routes.SessionController.login())
    }
  }

  def login: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.session.login())
  }

  // Clears the local session; the IdP end-session happens at /oidc/logout.
  def logout: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.session.loggedOut()).withNewSession
  }
}
```

Three tiny Twirl views under `app/views/session/`:

`login.scala.html`

```html
@()
<!DOCTYPE html>
<html>
<head><title>Sign in</title></head>
<body>
  <h1>Sign in</h1>
  <a href="/oidc/login">Login with SSO</a>
</body>
</html>
```

`loggedIn.scala.html`

```html
@(subject: String)
<!DOCTYPE html>
<html>
<head><title>Home</title></head>
<body>
  <h1>Signed in as @subject</h1>
  <a href="/oidc/logout">Logout</a>
</body>
</html>
```

`loggedOut.scala.html`

```html
@()
<!DOCTYPE html>
<html>
<head><title>Signed out</title></head>
<body>
  <h1>You are signed out</h1>
  <a href="/login">Sign in again</a>
</body>
</html>
```

Flow: `/login` shows the page → "Login with SSO" hits `/oidc/login` → IdP
authenticates → `/oidc/callback` creates the session and redirects to
`post_login_url` (e.g. `/`) → `/` reads `sub` from the session and shows the
logged-in page → "Logout" hits `/oidc/logout` → session cleared and IdP
end-session invoked.

## Customising the session (SessionCreator SPI)

By default the module stores `sub` plus the configured `session_claims` in the
Play session and redirects to `post_login_url`. To run your own logic
(database user sync, encrypted session keys, a custom redirect — as in a typical
enterprise `SessionController`), implement `SessionCreator` and bind it. Guice's
last-binding-wins makes your binding override the default.

```scala
package modules

import javax.inject.{Inject, Singleton}
import com.nimbusds.jwt.JWTClaimsSet
import play.api.mvc.{Request, Result, Results}
import io.ellatech.dfurmans.spi.SessionCreator

@Singleton
class MySessionCreator @Inject() (/* your services */) extends SessionCreator {
  override def onAuthenticated(claims: JWTClaimsSet, request: Request[?]): Result = {
    val email = Option(claims.getStringClaim("email")).getOrElse("")
    // sync user with your DB, build your own session, choose the redirect...
    Results.Redirect("/").withSession("userEmail" -> email)
  }
}
```

Bind it in a Guice module enabled by your app:

```scala
package modules

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import io.ellatech.dfurmans.spi.SessionCreator

class AppModule extends Module {
  def bindings(env: Environment, conf: Configuration): Seq[Binding[?]] =
    Seq(bind[SessionCreator].to[MySessionCreator])
}
```

## License

Apache License 2.0.

2026 io.ellatech
