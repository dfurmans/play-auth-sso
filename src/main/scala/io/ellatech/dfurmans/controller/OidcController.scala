package io.ellatech.dfurmans.controller

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import com.nimbusds.oauth2.sdk.AuthorizationCode
import com.nimbusds.oauth2.sdk.id.State
import com.nimbusds.openid.connect.sdk.Nonce
import com.typesafe.scalalogging.LazyLogging

import play.api.mvc.*

import io.ellatech.dfurmans.config.OidcConfigProvider
import io.ellatech.dfurmans.service.OidcService
import io.ellatech.dfurmans.spi.SessionCreator

/** OIDC authorization-code login/logout controller. Provides `login`,
  * `callback`, and `logout` actions. The authenticated outcome is delegated to
  * the injected `SessionCreator`.
  */
@Singleton
class OidcController @Inject() (
    cc: ControllerComponents,
    oidcService: OidcService,
    sessionCreator: SessionCreator,
    configProvider: OidcConfigProvider
)(implicit ec: ExecutionContext)
    extends AbstractController(cc)
    with LazyLogging {

  /** Start the login flow: store state+nonce in the session, redirect to the IdP. */
  def login: Action[AnyContent] = Action { implicit request =>
    val state = new State()
    val nonce = new Nonce()
    val authUrl = oidcService.buildAuthorizationUrl(state, nonce)
    logger.info("Redirecting to OIDC provider for authentication")
    Redirect(authUrl.toString).withSession(
      request.session + ("oidc_state" -> state.getValue) + ("oidc_nonce" -> nonce.getValue)
    )
  }

  /** Handle the IdP callback: verify state, exchange code, validate ID token. */
  def callback: Action[AnyContent] = Action.async { implicit request =>
    val checked = for {
      code          <- request.getQueryString("code").toRight("Missing authorization code")
      sessionState  <- request.session.get("oidc_state").toRight("Missing session state")
      sessionNonce  <- request.session.get("oidc_nonce").toRight("Missing session nonce")
      returnedState <- request.getQueryString("state").toRight("Missing state parameter")
      _             <- if (sessionState == returnedState) Right(()) else Left("State mismatch — possible CSRF")
    } yield (code, sessionNonce)

    checked match {
      case Left(error) =>
        logger.warn("OIDC callback validation failed: {}", error)
        Future.successful(Unauthorized(s"OIDC login failed: $error"))

      case Right((code, nonceValue)) =>
        Future {
          val result = for {
            tokenResponse <- oidcService.exchangeCode(new AuthorizationCode(code))
            claims        <- oidcService.validateAndExtractClaims(tokenResponse, new Nonce(nonceValue))
          } yield claims

          result match {
            case Success(claims) =>
              logger.info("OIDC user authenticated: subject {}", claims.getSubject)
              sessionCreator.onAuthenticated(claims, request)
            case Failure(ex) =>
              logger.error("OIDC token exchange/validation failed", ex)
              Unauthorized("OIDC authentication failed")
          }
        }
    }
  }

  /** Clear the session and redirect to the IdP end-session endpoint if present. */
  def logout: Action[AnyContent] = Action { implicit request =>
    oidcService.buildLogoutUrl("") match {
      case Some(uri) =>
        logger.info("Logging out via IdP end-session endpoint")
        Redirect(uri.toString).withNewSession
      case None =>
        Redirect(configProvider.config.postLogoutUrl).withNewSession
    }
  }
}
