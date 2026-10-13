package io.ellatech.dfurmans

import org.scalatestplus.play.PlaySpec
import play.api.inject.guice.GuiceApplicationBuilder

import io.ellatech.dfurmans.controller.OidcController
import io.ellatech.dfurmans.service.OidcService
import io.ellatech.dfurmans.spi.{DefaultSessionCreator, SessionCreator}

/** Thin wiring smoke test: boots a real Play application with the module and a
  * real OIDC configuration, and asserts the controller, service, and default
  * SessionCreator all inject. Lazy provider metadata is not resolved, so no
  * network call is made.
  */
class OidcModuleSpec extends PlaySpec {

  private def app = new GuiceApplicationBuilder()
    .configure(
      "nimbus.oidc.issuer"          -> "https://login.example.com",
      "nimbus.oidc.client_id"       -> "play-auth-sso",
      "nimbus.oidc.client_secret"   -> "secret",
      "nimbus.oidc.callback_url"    -> "https://app.example.com/oidc/callback",
      "nimbus.oidc.post_login_url"  -> "/home",
      "nimbus.oidc.post_logout_url" -> "/bye"
    )
    .build()

  "the OIDC module" should {

    "wire the controller, service, and default SessionCreator" in {
      val a = app
      try {
        val controller = a.injector.instanceOf[OidcController]
        val service    = a.injector.instanceOf[OidcService]
        val creator    = a.injector.instanceOf[SessionCreator]
        (controller != null && service != null && creator.isInstanceOf[DefaultSessionCreator]) mustBe true
      } finally {
        val _ = a.stop()
      }
    }
  }
}
