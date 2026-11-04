package io.ellatech.dfurmans.spi

import scala.concurrent.Future

import com.nimbusds.jwt.JWTClaimsSet
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import io.ellatech.dfurmans.config.OidcConfigProvider

class DefaultSessionCreatorSpec extends PlaySpec {

  private val creator = new DefaultSessionCreator(
    new OidcConfigProvider(
      Configuration.from(
        Map(
          "nimbus.oidc.issuer"          -> "https://login.example.com",
          "nimbus.oidc.client_id"       -> "play-auth-sso",
          "nimbus.oidc.client_secret"   -> "secret",
          "nimbus.oidc.callback_url"    -> "https://app.example.com/oidc/callback",
          "nimbus.oidc.post_login_url"  -> "/home",
          "nimbus.oidc.post_logout_url" -> "/bye",
          "nimbus.oidc.scopes"          -> java.util.List.of("openid"),
          "nimbus.oidc.session_claims"  -> java.util.List.of("email", "name")
        )
      )
    )
  )

  "DefaultSessionCreator" should {

    "redirect to post_login_url and store sub plus configured claims" in {
      val claims = new JWTClaimsSet.Builder()
        .subject("user-1")
        .claim("email", "a@example.com")
        .build()

      val result = Future.successful(creator.onAuthenticated(claims, FakeRequest()))

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/home")
      val s = session(result)
      s.get("sub") mustBe Some("user-1")
      s.get("email") mustBe Some("a@example.com")
    }

    "skip configured claims that are absent from the token" in {
      val claims = new JWTClaimsSet.Builder().subject("user-2").build()

      val result = Future.successful(creator.onAuthenticated(claims, FakeRequest()))

      val s = session(result)
      s.get("sub") mustBe Some("user-2")
      s.get("email") mustBe None
      s.get("name") mustBe None
    }
  }
}
