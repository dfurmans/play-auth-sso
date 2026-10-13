package io.ellatech.dfurmans.spi

import javax.inject.{Inject, Singleton}

import com.nimbusds.jwt.JWTClaimsSet

import play.api.mvc.{Request, Result, Results}

import io.ellatech.dfurmans.config.OidcConfigProvider

/** SPI invoked after a successful OIDC authentication. The host application
  * implements this to create its own session (user sync, encryption, custom
  * session keys) and decide where to redirect. Bind a custom implementation via
  * Guice to override the default.
  */
trait SessionCreator {

  /** @param claims  the validated ID-token claims
    * @param request the current callback request
    * @return the Result to send to the browser (typically a redirect that sets
    *         a session)
    */
  def onAuthenticated(claims: JWTClaimsSet, request: Request[?]): Result
}

/** Default `SessionCreator`: stores the subject plus the configured
  * `session_claims` in the Play session and redirects to the configured
  * post-login URL.
  */
@Singleton
class DefaultSessionCreator @Inject() (configProvider: OidcConfigProvider) extends SessionCreator {

  override def onAuthenticated(claims: JWTClaimsSet, request: Request[?]): Result = {
    val cfg = configProvider.config

    val base: Seq[(String, String)] = Seq("sub" -> claims.getSubject)
    val extra: Seq[(String, String)] = cfg.sessionClaims.flatMap { name =>
      Option(claims.getClaim(name)).map(v => name -> String.valueOf(v))
    }

    Results.Redirect(cfg.postLoginUrl).withSession((base ++ extra)*)
  }
}
