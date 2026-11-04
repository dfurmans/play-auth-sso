package io.ellatech.dfurmans.service

import java.net.URI
import java.util.List as JList

import com.nimbusds.jose.JWSAlgorithm
import org.scalatestplus.play.PlaySpec

class OidcServiceSpec extends PlaySpec {

  "OidcService.logoutUri" should {

    "URL-encode the redirect and the id_token_hint" in {
      val uri = OidcService.logoutUri(
        new URI("https://idp.example.com/logout"),
        "https://app.example.com/bye?a=1&b=2",
        "a.b c"
      )
      uri.toString mustBe
        "https://idp.example.com/logout?post_logout_redirect_uri=https%3A%2F%2Fapp.example.com%2Fbye%3Fa%3D1%26b%3D2&id_token_hint=a.b+c"
    }

    "omit id_token_hint when empty" in {
      val uri = OidcService.logoutUri(new URI("https://idp.example.com/logout"), "/bye", "")
      uri.toString mustBe "https://idp.example.com/logout?post_logout_redirect_uri=%2Fbye"
    }

    "append to an existing query string" in {
      val uri = OidcService.logoutUri(new URI("https://idp.example.com/logout?x=1"), "/bye", "")
      uri.toString mustBe "https://idp.example.com/logout?x=1&post_logout_redirect_uri=%2Fbye"
    }
  }

  "OidcService.selectSigningAlgorithm" should {

    "default to RS256 when the provider advertises nothing" in {
      OidcService.selectSigningAlgorithm(null) mustBe JWSAlgorithm.RS256
      OidcService.selectSigningAlgorithm(JList.of()) mustBe JWSAlgorithm.RS256
    }

    "prefer RS256 when advertised" in {
      OidcService.selectSigningAlgorithm(JList.of(JWSAlgorithm.ES256, JWSAlgorithm.RS256)) mustBe JWSAlgorithm.RS256
    }

    "fall back to the first advertised algorithm" in {
      OidcService.selectSigningAlgorithm(JList.of(JWSAlgorithm.ES256, JWSAlgorithm.PS256)) mustBe JWSAlgorithm.ES256
    }
  }
}
