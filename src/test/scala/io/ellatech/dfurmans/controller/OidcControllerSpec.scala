package io.ellatech.dfurmans.controller

import org.scalatestplus.play.PlaySpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.*

/** Callback validation paths that are decided before any network call. */
class OidcControllerSpec extends PlaySpec {

  private val flowSession = Seq("oidc_state" -> "s1", "oidc_nonce" -> "n1", "oidc_pkce" -> "v1")

  private def withController[T](test: OidcController => T): T = {
    val app = new GuiceApplicationBuilder()
      .configure(
        "nimbus.oidc.issuer"          -> "https://login.example.com",
        "nimbus.oidc.client_id"       -> "play-auth-sso",
        "nimbus.oidc.client_secret"   -> "secret",
        "nimbus.oidc.callback_url"    -> "https://app.example.com/oidc/callback",
        "nimbus.oidc.post_login_url"  -> "/home",
        "nimbus.oidc.post_logout_url" -> "/bye"
      )
      .build()
    try test(app.injector.instanceOf[OidcController])
    finally { val _ = app.stop() }
  }

  "OidcController.callback" should {

    "reject an error response from the IdP" in withController { c =>
      val result = c.callback()(
        FakeRequest(GET, "/cb?error=access_denied&error_description=nope").withSession(flowSession*)
      )
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) must include("access_denied")
    }

    "reject a missing authorization code" in withController { c =>
      val result = c.callback()(FakeRequest(GET, "/cb?state=s1").withSession(flowSession*))
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) must include("Missing authorization code")
    }

    "reject a state mismatch" in withController { c =>
      val result = c.callback()(FakeRequest(GET, "/cb?code=abc&state=other").withSession(flowSession*))
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) must include("State mismatch")
    }

    "reject a callback without the PKCE verifier" in withController { c =>
      val result = c.callback()(
        FakeRequest(GET, "/cb?code=abc&state=s1").withSession("oidc_state" -> "s1", "oidc_nonce" -> "n1")
      )
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) must include("Missing PKCE verifier")
    }

    "clear the one-time flow values from the session on failure" in withController { c =>
      val result = c.callback()(FakeRequest(GET, "/cb?code=abc&state=other").withSession(flowSession*))
      val s      = session(result)
      s.get("oidc_state") mustBe None
      s.get("oidc_nonce") mustBe None
      s.get("oidc_pkce") mustBe None
    }
  }
}
