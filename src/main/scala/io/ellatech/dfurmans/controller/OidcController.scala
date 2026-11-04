package io.ellatech.dfurmans.controller

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import org.apache.pekko.actor.ActorSystem

import com.nimbusds.oauth2.sdk.AuthorizationCode
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier
import com.nimbusds.oauth2.sdk.id.State
import com.nimbusds.openid.connect.sdk.Nonce
import com.typesafe.scalalogging.LazyLogging

import play.api.mvc.*

import io.ellatech.dfurmans.config.OidcConfigProvider
import io.ellatech.dfurmans.service.OidcService
import io.ellatech.dfurmans.spi.{IdTokenStore, SessionCreator}

/** OIDC authorization-code login/logout controller. Provides `login`,
  * `callback`, and `logout` actions. The authenticated outcome is delegated to
  * the injected `SessionCreator`.
  */
@Singleton
class OidcController @Inject() (
    cc: ControllerComponents,
    oidcService: OidcService,
    sessionCreator: SessionCreator,
    configProvider: OidcConfigProvider,
    idTokenStore: IdTokenStore,
    actorSystem: ActorSystem
)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
    with LazyLogging {

  private val IdTokenKeySessionKey = "oidc_sid"
  private val StateKey          = "oidc_state"
  private val NonceKey          = "oidc_nonce"
  private val PkceKey           = "oidc_pkce"

  /** Dedicated pool for blocking IdP calls (discovery, token exchange, JWKS). */
  private val blockingEc: ExecutionContext = actorSystem.dispatchers.lookup("nimbus.blocking-dispatcher")

  /** Start the login flow: store state, nonce and PKCE verifier in the session,
    * redirect to the IdP.
    */
  def login: Action[AnyContent] = Action.async { implicit request =>
    val state    = new State()
    val nonce    = new Nonce()
    val verifier = new CodeVerifier()
    // Resolving provider metadata may hit the network on first use.
    Future(oidcService.buildAuthorizationUrl(state, nonce, verifier))(blockingEc).map { authUrl =>
      logger.info("Redirecting to OIDC provider for authentication")
      Redirect(authUrl.toString).withSession(
        request.session + (StateKey -> state.getValue) + (NonceKey -> nonce.getValue) + (PkceKey -> verifier.getValue)
      )
    }
  }

  /** Handle the IdP callback: verify state, exchange code, validate ID token. */
  def callback: Action[AnyContent] = Action.async { implicit request =>
    val idpError: Either[String, Unit] = request.getQueryString("error") match {
      case Some(err) =>
        logger.warn("IdP returned error '{}': {}", err, request.getQueryString("error_description").getOrElse(""))
        Left(s"Identity provider returned error '$err'")
      case None => Right(())
    }

    val checked = for {
      _             <- idpError
      code          <- request.getQueryString("code").toRight("Missing authorization code")
      sessionState  <- request.session.get(StateKey).toRight("Missing session state")
      sessionNonce  <- request.session.get(NonceKey).toRight("Missing session nonce")
      verifier      <- request.session.get(PkceKey).toRight("Missing PKCE verifier")
      returnedState <- request.getQueryString("state").toRight("Missing state parameter")
      _             <- if (sessionState == returnedState) Right(()) else Left("State mismatch — possible CSRF")
    } yield (code, sessionNonce, verifier)

    // One-time values: never leave them in the session once the callback was seen.
    def clearFlowState(result: Result): Result = result.removingFromSession(StateKey, NonceKey, PkceKey)

    checked match {
      case Left(error) =>
        logger.warn("OIDC callback validation failed: {}", error)
        Future.successful(clearFlowState(Unauthorized(s"OIDC login failed: $error")))

      case Right((code, nonceValue, verifier)) =>
        Future {
          val result = for {
            tokenResponse <- oidcService.exchangeCode(new AuthorizationCode(code), new CodeVerifier(verifier))
            claims        <- oidcService.validateAndExtractClaims(tokenResponse, new Nonce(nonceValue))
          } yield (tokenResponse.getOIDCTokens.getIDTokenString, claims)

          result match {
            case Success((rawIdToken, claims)) =>
              logger.info("OIDC user authenticated: subject {}", claims.getSubject)
              clearFlowState(
                sessionCreator
                  .onAuthenticated(claims, request)
                  .addingToSession(IdTokenKeySessionKey -> idTokenStore.put(rawIdToken))
              )
            case Failure(ex) =>
              logger.error("OIDC token exchange/validation failed", ex)
              clearFlowState(Unauthorized("OIDC authentication failed"))
          }
        }(blockingEc)
    }
  }

  /** Clear the session and redirect to the IdP end-session endpoint if present. */
  def logout: Action[AnyContent] = Action.async { implicit request =>
    val storeKey    = request.session.get(IdTokenKeySessionKey)
    val idTokenHint = storeKey.flatMap(idTokenStore.get).getOrElse("")
    storeKey.foreach(idTokenStore.remove)
    Future(oidcService.buildLogoutUrl(idTokenHint))(blockingEc).map {
      case Some(uri) =>
        logger.info("Logging out via IdP end-session endpoint")
        Redirect(uri.toString).withNewSession
      case None =>
        Redirect(configProvider.config.postLogoutUrl.value).withNewSession
    }
  }
}
