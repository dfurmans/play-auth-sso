package io.ellatech.dfurmans.spi

import java.security.SecureRandom
import java.time.{Clock, Instant}
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

import javax.inject.{Inject, Singleton}

import scala.concurrent.duration.FiniteDuration

import play.api.Configuration

/** Server-side storage for the raw ID token, so it never has to travel in the
  * (size-limited) session cookie. The token is only needed at logout, as the
  * `id_token_hint`. Only an opaque key is placed in the session. Bind a custom
  * implementation (Redis, database, ...) via Guice for multi-node deployments.
  */
trait IdTokenStore {

  /** Store the token and return the opaque key to keep in the session. */
  def put(idToken: String): String

  def get(key: String): Option[String]

  def remove(key: String): Unit
}

/** In-memory `IdTokenStore` with a TTL (`nimbus.oidc.id_token_ttl`). Entries
  * are lost on restart and are not shared between nodes; in that case logout
  * simply proceeds without an `id_token_hint`.
  */
@Singleton
class DefaultIdTokenStore(ttl: FiniteDuration, clock: Clock) extends IdTokenStore {

  @Inject
  def this(configuration: Configuration) =
    this(configuration.get[FiniteDuration]("nimbus.oidc.id_token_ttl"), Clock.systemUTC())

  private final case class Entry(idToken: String, expiresAt: Instant)

  private val entries   = new ConcurrentHashMap[String, Entry]()
  private val random    = new SecureRandom()
  private val lastPurge = new AtomicReference[Instant](Instant.EPOCH)

  override def put(idToken: String): String = {
    purgeExpired()
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    val key = Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
    entries.put(key, Entry(idToken, clock.instant().plusMillis(ttl.toMillis)))
    key
  }

  override def get(key: String): Option[String] =
    Option(entries.get(key)).filter(_.expiresAt.isAfter(clock.instant())).map(_.idToken)

  override def remove(key: String): Unit = {
    val _ = entries.remove(key)
  }

  /** Drop expired entries, at most once a minute. */
  private def purgeExpired(): Unit = {
    val now  = clock.instant()
    val last = lastPurge.get()
    if (last.plusSeconds(60).isBefore(now) && lastPurge.compareAndSet(last, now)) {
      entries.entrySet().removeIf(e => !e.getValue.expiresAt.isAfter(now))
      ()
    }
  }
}
