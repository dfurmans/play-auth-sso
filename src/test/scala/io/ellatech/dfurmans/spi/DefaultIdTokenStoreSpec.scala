package io.ellatech.dfurmans.spi

import java.time.{Clock, Instant, ZoneId, ZoneOffset}

import scala.concurrent.duration.*

import org.scalatestplus.play.PlaySpec

class DefaultIdTokenStoreSpec extends PlaySpec {

  private class MutableClock(var now: Instant) extends Clock {
    override def getZone: ZoneId                = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock  = this
    override def instant(): Instant             = now
  }

  "DefaultIdTokenStore" should {

    "return the stored token for the issued key" in {
      val store = new DefaultIdTokenStore(1.hour, Clock.systemUTC())
      val key   = store.put("raw.id.token")
      store.get(key) mustBe Some("raw.id.token")
    }

    "issue a distinct opaque key per token that does not contain the token" in {
      val store = new DefaultIdTokenStore(1.hour, Clock.systemUTC())
      val k1    = store.put("raw.id.token")
      val k2    = store.put("raw.id.token")
      k1 must not be k2
      k1 must not include "raw"
    }

    "forget a removed token" in {
      val store = new DefaultIdTokenStore(1.hour, Clock.systemUTC())
      val key   = store.put("t")
      store.remove(key)
      store.get(key) mustBe None
    }

    "return None for an unknown key" in {
      new DefaultIdTokenStore(1.hour, Clock.systemUTC()).get("nope") mustBe None
    }

    "expire tokens after the TTL" in {
      val clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"))
      val store = new DefaultIdTokenStore(1.hour, clock)
      val key   = store.put("t")
      clock.now = clock.now.plusSeconds(3599)
      store.get(key) mustBe Some("t")
      clock.now = clock.now.plusSeconds(2)
      store.get(key) mustBe None
    }
  }
}
