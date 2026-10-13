package io.ellatech.dfurmans

import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

import io.ellatech.dfurmans.spi.{DefaultSessionCreator, SessionCreator}

/** Guice module. Binds the default `SessionCreator`; a host application
  * overrides it by binding its own implementation (last binding wins).
  * Registered automatically via `reference.conf`.
  */
class NimbusModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] =
    Seq(
      bind[SessionCreator].to[DefaultSessionCreator]
    )
}
