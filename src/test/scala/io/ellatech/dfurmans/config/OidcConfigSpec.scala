package io.ellatech.dfurmans.config

import org.scalatestplus.play.PlaySpec
import play.api.Configuration

class OidcConfigSpec extends PlaySpec {

  private def provider(overrides: (String, Any)*): OidcConfigProvider = {
    val defaults = Map[String, Any](
      "nimbus.oidc.issuer"          -> "https://login.example.com",
      "nimbus.oidc.client_id"       -> "play-auth-sso",
      "nimbus.oidc.client_secret"   -> "s3cr3t",
      "nimbus.oidc.callback_url"    -> "https://app.example.com/oidc/callback",
      "nimbus.oidc.post_login_url"  -> "/home",
      "nimbus.oidc.post_logout_url" -> "/bye",
      "nimbus.oidc.scopes"          -> java.util.List.of("openid", "email"),
      "nimbus.oidc.session_claims"  -> java.util.List.of("email", "name")
    )
    new OidcConfigProvider(Configuration.from(defaults ++ overrides))
  }

  "OidcConfigProvider" should {

    "map every configuration key to its typed field" in {
      val cfg = provider().config
      cfg.issuer.value mustBe "https://login.example.com"
      cfg.clientId.value mustBe "play-auth-sso"
      cfg.clientSecret.value mustBe "s3cr3t"
      cfg.callbackUrl.value mustBe "https://app.example.com/oidc/callback"
      cfg.postLoginUrl.value mustBe "/home"
      cfg.postLogoutUrl.value mustBe "/bye"
      cfg.scopes.map(_.value) mustBe Seq("openid", "email")
      cfg.sessionClaims.map(_.value) mustBe Seq("email", "name")
    }

    "fail when a required key is missing" in {
      an[Exception] must be thrownBy new OidcConfigProvider(Configuration.from(Map("nimbus.oidc.issuer" -> "x")))
    }
  }

  "OidcConfigProvider validation" should {

    "reject an empty issuer" in {
      the[IllegalArgumentException] thrownBy provider("nimbus.oidc.issuer" -> "") must have message
        "requirement failed: Configuration 'nimbus.oidc.issuer' must not be empty"
    }

    "reject an empty client secret" in {
      an[IllegalArgumentException] must be thrownBy provider("nimbus.oidc.client_secret" -> " ")
    }

    "reject a relative callback URL" in {
      an[IllegalArgumentException] must be thrownBy provider("nimbus.oidc.callback_url" -> "/oidc/callback")
    }

    "reject a malformed issuer URI" in {
      an[IllegalArgumentException] must be thrownBy provider("nimbus.oidc.issuer" -> "http://bad host")
    }

    "accept relative post-login and post-logout URLs" in {
      val cfg = provider("nimbus.oidc.post_login_url" -> "/", "nimbus.oidc.post_logout_url" -> "/").config
      cfg.postLoginUrl.value mustBe "/"
    }
  }

  "ClientSecret" should {

    "not reveal the secret in toString" in {
      val rendered = provider().config.toString
      rendered must not include "s3cr3t"
      ClientSecret("s3cr3t").toString mustBe "ClientSecret(***)"
    }
  }
}
