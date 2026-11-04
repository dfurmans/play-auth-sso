package io.ellatech.dfurmans.config

import java.net.URI

import javax.inject.{Inject, Singleton}

import scala.util.Try

import play.api.Configuration

/** Typed OIDC configuration read from `nimbus.oidc.*`. */
final case class OidcConfig(
    issuer: Issuer,
    clientId: ClientId,
    clientSecret: ClientSecret,
    callbackUrl: CallbackUrl,
    postLoginUrl: PostLoginUrl,
    postLogoutUrl: PostLogoutUrl,
    scopes: Seq[OidcScope],
    sessionClaims: Seq[ClaimName]
)

opaque type Issuer = String
object Issuer {
  def apply(value: String): Issuer = value
  extension (i: Issuer) def value: String = i
}

opaque type ClientId = String
object ClientId {
  def apply(value: String): ClientId = value
  extension (c: ClientId) def value: String = c
}

/** Redacts itself in `toString` so the secret never reaches logs. */
final case class ClientSecret(value: String) {
  override def toString: String = "ClientSecret(***)"
}

opaque type CallbackUrl = String
object CallbackUrl {
  def apply(value: String): CallbackUrl = value
  extension (u: CallbackUrl) def value: String = u
}

opaque type PostLoginUrl = String
object PostLoginUrl {
  def apply(value: String): PostLoginUrl = value
  extension (u: PostLoginUrl) def value: String = u
}

opaque type PostLogoutUrl = String
object PostLogoutUrl {
  def apply(value: String): PostLogoutUrl = value
  extension (u: PostLogoutUrl) def value: String = u
}

opaque type OidcScope = String
object OidcScope {
  def apply(value: String): OidcScope = value
  extension (s: OidcScope) def value: String = s
}

opaque type ClaimName = String
object ClaimName {
  def apply(value: String): ClaimName = value
  extension (c: ClaimName) def value: String = c
}

@Singleton
class OidcConfigProvider @Inject() (configuration: Configuration) {

  private def required(key: String): String = {
    val value = configuration.get[String](s"nimbus.oidc.$key").trim
    require(value.nonEmpty, s"Configuration 'nimbus.oidc.$key' must not be empty")
    value
  }

  private def uri(key: String, absolute: Boolean): String = {
    val value = required(key)
    val parsed = Try(new URI(value)).getOrElse(
      throw new IllegalArgumentException(s"Configuration 'nimbus.oidc.$key' is not a valid URI: $value")
    )
    require(
      !absolute || parsed.isAbsolute,
      s"Configuration 'nimbus.oidc.$key' must be an absolute URI: $value"
    )
    value
  }

  val config: OidcConfig = OidcConfig(
    issuer = Issuer(uri("issuer", absolute = true)),
    clientId = ClientId(required("client_id")),
    clientSecret = ClientSecret(required("client_secret")),
    callbackUrl = CallbackUrl(uri("callback_url", absolute = true)),
    postLoginUrl = PostLoginUrl(uri("post_login_url", absolute = false)),
    postLogoutUrl = PostLogoutUrl(uri("post_logout_url", absolute = false)),
    scopes = configuration.get[Seq[String]]("nimbus.oidc.scopes").map(OidcScope(_)),
    sessionClaims = configuration.get[Seq[String]]("nimbus.oidc.session_claims").map(ClaimName(_))
  )
}
