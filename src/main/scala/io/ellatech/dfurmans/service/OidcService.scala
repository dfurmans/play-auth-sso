package io.ellatech.dfurmans.service

import java.net.{URI, URLEncoder}
import java.nio.charset.StandardCharsets

import javax.inject.{Inject, Singleton}

import scala.jdk.CollectionConverters.*
import scala.util.Try

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.{JWKSource, JWKSourceBuilder}
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.oauth2.sdk.*
import com.nimbusds.oauth2.sdk.auth.{ClientAuthentication, ClientSecretBasic, Secret}
import com.nimbusds.oauth2.sdk.id.{ClientID, Issuer, State}
import com.nimbusds.oauth2.sdk.pkce.{CodeChallengeMethod, CodeVerifier}
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

  private val clientID: ClientID = new ClientID(cfg.clientId.value)
  private val clientSecret: Secret = new Secret(cfg.clientSecret.value)
  private val clientAuth: ClientAuthentication = new ClientSecretBasic(clientID, clientSecret)
  private val callbackURI: URI = new URI(cfg.callbackUrl.value)

  /** Lazily resolved OIDC provider metadata from the discovery endpoint. */
  lazy val providerMetadata: OIDCProviderMetadata = {
    logger.info("Resolving OIDC provider metadata for issuer {}", cfg.issuer.value)
    OIDCProviderMetadata.resolve(new Issuer(cfg.issuer.value), OidcService.HttpTimeoutMs, OidcService.HttpTimeoutMs)
  }

  /** Built once: the cached JWK source avoids fetching the JWKS on every
    * validation, and the signing algorithm comes from the provider metadata.
    */
  private lazy val idTokenValidator: IDTokenValidator = {
    val jwkSource: JWKSource[SecurityContext] =
      JWKSourceBuilder.create[SecurityContext](providerMetadata.getJWKSetURI.toURL).build()
    new IDTokenValidator(
      providerMetadata.getIssuer,
      clientID,
      new JWSVerificationKeySelector[SecurityContext](
        OidcService.selectSigningAlgorithm(providerMetadata.getIDTokenJWSAlgs),
        jwkSource
      ),
      null
    )
  }

  /** Build the authorization redirect URL for the OIDC login. */
  def buildAuthorizationUrl(state: State, nonce: Nonce, verifier: CodeVerifier): URI = {
    val scope = new Scope(cfg.scopes.map(_.value)*)
    new AuthenticationRequest.Builder(
      new ResponseType(ResponseType.Value.CODE),
      scope,
      clientID,
      callbackURI
    )
      .endpointURI(providerMetadata.getAuthorizationEndpointURI)
      .state(state)
      .nonce(nonce)
      .codeChallenge(verifier, CodeChallengeMethod.S256)
      .build()
      .toURI
  }

  /** Exchange the authorization code for tokens. */
  def exchangeCode(code: AuthorizationCode, verifier: CodeVerifier): Try[OIDCTokenResponse] = Try {
    val tokenRequest = new TokenRequest.Builder(
      providerMetadata.getTokenEndpointURI,
      clientAuth,
      new AuthorizationCodeGrant(code, callbackURI, verifier)
    ).build()
    val httpRequest = tokenRequest.toHTTPRequest
    httpRequest.setConnectTimeout(OidcService.HttpTimeoutMs)
    httpRequest.setReadTimeout(OidcService.HttpTimeoutMs)
    val tokenResponse = OIDCTokenResponseParser.parse(httpRequest.send())
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
    val claims = idTokenValidator.validate(idToken, expectedNonce)
    logger.debug("ID token validated for subject {}", claims.getSubject)
    idToken.getJWTClaimsSet
  }

  /** Build the IdP end-session (logout) URL, if the provider advertises one.
    *
    * `postLogoutRedirectUri` must be an absolute URL registered with the IdP as
    * a valid post-logout redirect; IdPs such as Keycloak reject a relative or
    * unregistered value with "Invalid redirect uri" and refuse to end the
    * session.
    */
  def buildLogoutUrl(postLogoutRedirectUri: String, idTokenHint: String): Option[URI] =
    Option(providerMetadata.getEndSessionEndpointURI).map { endSession =>
      OidcService.logoutUri(endSession, postLogoutRedirectUri, idTokenHint)
    }
}

object OidcService {

  /** Connect/read timeout for IdP HTTP calls (discovery, token endpoint). */
  private[service] val HttpTimeoutMs: Int = 5000

  /** RS256 is the OIDC default; otherwise take the first algorithm the
    * provider advertises.
    */
  private[service] def selectSigningAlgorithm(advertised: java.util.List[JWSAlgorithm]): JWSAlgorithm =
    Option(advertised).map(_.asScala.toList).getOrElse(Nil) match {
      case algs if algs.isEmpty || algs.contains(JWSAlgorithm.RS256) => JWSAlgorithm.RS256
      case algs                                                      => algs.head
    }

  /** Append URL-encoded logout parameters, preserving any query already on the
    * end-session endpoint.
    */
  private[service] def logoutUri(endSession: URI, postLogoutUrl: String, idTokenHint: String): URI = {
    def enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)
    val params = Seq("post_logout_redirect_uri" -> postLogoutUrl) ++
      Option(idTokenHint).filter(_.nonEmpty).map("id_token_hint" -> _)
    val query = params.map((k, v) => s"$k=${enc(v)}").mkString("&")
    val sep   = if (endSession.getRawQuery == null) "?" else "&"
    new URI(s"$endSession$sep$query")
  }
}
