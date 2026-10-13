package io.ellatech.dfurmans.service

import java.net.URI

import javax.inject.{Inject, Singleton}

import scala.util.Try

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.oauth2.sdk.*
import com.nimbusds.oauth2.sdk.auth.{ClientAuthentication, ClientSecretBasic, Secret}
import com.nimbusds.oauth2.sdk.id.{ClientID, Issuer, State}
import com.nimbusds.openid.connect.sdk.*
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator
import com.typesafe.scalalogging.LazyLogging

import io.ellatech.dfurmans.config.OidcConfigProvider

/** OIDC authorization-code flow engine built on the Nimbus SDK. Mirrors the
  * house `OidcService` conventions: lazy provider metadata, `Try` for fallible
  * operations, `LazyLogging`.
  */
@Singleton
class OidcService @Inject() (configProvider: OidcConfigProvider) extends LazyLogging {

  private val cfg = configProvider.config

  private val clientID: ClientID = new ClientID(cfg.clientId)
  private val clientSecret: Secret = new Secret(cfg.clientSecret)
  private val clientAuth: ClientAuthentication = new ClientSecretBasic(clientID, clientSecret)
  private val callbackURI: URI = new URI(cfg.callbackUrl)

  /** Lazily resolved OIDC provider metadata from the discovery endpoint. */
  lazy val providerMetadata: OIDCProviderMetadata = {
    logger.info("Resolving OIDC provider metadata for issuer {}", cfg.issuer)
    OIDCProviderMetadata.resolve(new Issuer(cfg.issuer))
  }

  /** Build the authorization redirect URL for the OIDC login. */
  def buildAuthorizationUrl(state: State, nonce: Nonce): URI = {
    val scope = new Scope(cfg.scopes*)
    new AuthenticationRequest.Builder(
      new ResponseType(ResponseType.Value.CODE),
      scope,
      clientID,
      callbackURI
    )
      .endpointURI(providerMetadata.getAuthorizationEndpointURI)
      .state(state)
      .nonce(nonce)
      .build()
      .toURI
  }

  /** Exchange the authorization code for tokens. */
  def exchangeCode(code: AuthorizationCode): Try[OIDCTokenResponse] = Try {
    val tokenRequest = new TokenRequest(
      providerMetadata.getTokenEndpointURI,
      clientAuth,
      new AuthorizationCodeGrant(code, callbackURI)
    )
    val tokenResponse = OIDCTokenResponseParser.parse(tokenRequest.toHTTPRequest.send())
    if (!tokenResponse.indicatesSuccess()) {
      val err = tokenResponse.toErrorResponse.getErrorObject
      throw new RuntimeException(s"Token exchange failed: ${err.getDescription}")
    }
    tokenResponse.toSuccessResponse.asInstanceOf[OIDCTokenResponse]
  }

  /** Validate the ID token (signature, issuer, audience, expiry, nonce) and
    * return its claims.
    */
  def validateAndExtractClaims(tokenResponse: OIDCTokenResponse, expectedNonce: Nonce): Try[JWTClaimsSet] = Try {
    val idToken = tokenResponse.getOIDCTokens.getIDToken
    val validator = new IDTokenValidator(
      providerMetadata.getIssuer,
      clientID,
      JWSAlgorithm.RS256,
      providerMetadata.getJWKSetURI.toURL
    )
    val claims = validator.validate(idToken, expectedNonce)
    logger.debug("ID token validated for subject {}", claims.getSubject)
    idToken.getJWTClaimsSet
  }

  /** Build the IdP end-session (logout) URL, if the provider advertises one. */
  def buildLogoutUrl(idTokenHint: String): Option[URI] =
    Option(providerMetadata.getEndSessionEndpointURI).map { endSession =>
      val hint = if (idTokenHint.nonEmpty) s"&id_token_hint=$idTokenHint" else ""
      new URI(s"$endSession?post_logout_redirect_uri=${cfg.postLogoutUrl}$hint")
    }
}
