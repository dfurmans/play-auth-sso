package io.ellatech.dfurmans.config

import javax.inject.{Inject, Singleton}

import play.api.Configuration

/** Typed OIDC configuration read from `nimbus.oidc.*`. */
final case class OidcConfig(
    issuer: String,
    clientId: String,
    clientSecret: String,
    callbackUrl: String,
    postLoginUrl: String,
    postLogoutUrl: String,
    scopes: Seq[String],
    sessionClaims: Seq[String]
)

@Singleton
class OidcConfigProvider @Inject() (configuration: Configuration) {

  val config: OidcConfig = OidcConfig(
    issuer = configuration.get[String]("nimbus.oidc.issuer"),
    clientId = configuration.get[String]("nimbus.oidc.client_id"),
    clientSecret = configuration.get[String]("nimbus.oidc.client_secret"),
    callbackUrl = configuration.get[String]("nimbus.oidc.callback_url"),
    postLoginUrl = configuration.get[String]("nimbus.oidc.post_login_url"),
    postLogoutUrl = configuration.get[String]("nimbus.oidc.post_logout_url"),
    scopes = configuration.get[Seq[String]]("nimbus.oidc.scopes"),
    sessionClaims = configuration.get[Seq[String]]("nimbus.oidc.session_claims")
  )
}
